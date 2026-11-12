package com.epictalenttrust.hr.mail;

import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.security.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Emails to people who aren't candidates: panel members, partner assessors, colleagues covering leave. Each email is
 * saved first and sent once the change that created it is saved. If sending fails it is tried again after 2, 10 and
 * 30 minutes; if it still fails, HR is told so they can pass the details on another way.
 */
@Component
public class Outbox {

    public record Email(UUID companyId, UUID jobId, UUID interviewId, String purpose, String to, String toName,
                        String subject, String body) {
    }

    record Queued(UUID id) {
    }

    private static final Logger log = LoggerFactory.getLogger(Outbox.class);
    private static final List<Duration> RETRIES = List.of(Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofMinutes(30));

    private final JdbcClient jdbc;
    private final MailGateway gateway;
    private final TemplateEngine templates;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String brandColor;
    private final String headingColor;

    public Outbox(JdbcClient jdbc, MailGateway gateway, TemplateEngine templates, NotificationService notifications,
                  ApplicationEventPublisher events, Clock clock, EttProperties properties) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.templates = templates;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
        this.brandColor = hex(properties.mail().brandColor(), "#0a7476");
        this.headingColor = hex(properties.mail().headingColor(), "#0b2545");
    }

    /** Saves the email; it goes out once the current transaction commits. */
    public UUID queue(Email email) {
        UUID id = jdbc.sql("""
                        insert into email_outbox (company_id, job_id, interview_id, purpose, to_address, to_name, subject, body)
                        values (:c, :j, :i, :p, :to, :name, :s, :b) returning id
                        """)
                .param("c", email.companyId()).param("j", email.jobId()).param("i", email.interviewId())
                .param("p", email.purpose()).param("to", email.to()).param("name", email.toName())
                .param("s", email.subject()).param("b", email.body())
                .query(UUID.class).single();
        events.publishEvent(new Queued(id));
        return id;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueued(Queued queued) {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> send(queued.id()));
    }

    /** Tries again any email whose retry time has come. */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void retryDue() {
        List<UUID> due = jdbc.sql("""
                        select id from email_outbox
                         where sent_at is null and failed_at is null and send_attempts > 0 and next_attempt_at <= now()
                         order by next_attempt_at limit 50
                        """)
                .query(UUID.class).list();
        due.forEach(id -> ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> send(id)));
    }

    void send(UUID id) {
        record Row(UUID companyId, UUID jobId, UUID interviewId, String to, String toName, String subject, String body,
                   int attempts, Instant sentAt) {
        }
        Row row = jdbc.sql("""
                        select company_id, job_id, interview_id, to_address, to_name, subject, body, send_attempts, sent_at
                          from email_outbox where id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new Row(Rows.uuid(rs, "company_id"), Rows.uuid(rs, "job_id"), Rows.uuid(rs, "interview_id"),
                        rs.getString("to_address"), rs.getString("to_name"), rs.getString("subject"), rs.getString("body"),
                        rs.getInt("send_attempts"), Rows.instant(rs, "sent_at")))
                .optional().orElse(null);
        if (row == null || row.sentAt() != null) {
            return;
        }
        try {
            gateway.send(row.to(), row.subject(), row.body(), html(row.subject(), row.body()));
            jdbc.sql("update email_outbox set sent_at = :at, send_attempts = send_attempts + 1 where id = :id")
                    .param("at", Timestamp.from(clock.instant())).param("id", id).update();
            log(row.to(), "sent", null);
        } catch (Exception ex) {
            int attempts = row.attempts() + 1;
            log.warn("Email {} to {} failed (attempt {}): {}", id, row.to(), attempts, ex.getMessage());
            log(row.to(), "failed", ex.getMessage());
            if (attempts > RETRIES.size()) {
                jdbc.sql("""
                        update email_outbox set send_attempts = :n, failed_at = :at, failure_reason = :r where id = :id
                        """)
                        .param("n", attempts).param("at", Timestamp.from(clock.instant()))
                        .param("r", ex.getMessage()).param("id", id).update();
                tellHr(row.companyId(), row.jobId(), row.interviewId(), row.toName() == null ? row.to() : row.toName(),
                        row.subject());
            } else {
                jdbc.sql("update email_outbox set send_attempts = :n, next_attempt_at = :next, failure_reason = :r where id = :id")
                        .param("n", attempts).param("next", Timestamp.from(clock.instant().plus(RETRIES.get(attempts - 1))))
                        .param("r", ex.getMessage()).param("id", id).update();
            }
        }
    }

    private void tellHr(UUID companyId, UUID jobId, UUID interviewId, String who, String subject) {
        if (companyId == null) {
            return;
        }
        List<UUID> recipients = jobId != null ? List.of(notifications.jobOwner(jobId))
                : jdbc.sql("select id from hr_users where company_id = :c and role = 'admin' and status = 'active'")
                        .param("c", companyId).query(UUID.class).list();
        UUID applicationId = interviewId == null ? null
                : jdbc.sql("select application_id from interviews where id = :i").param("i", interviewId)
                        .query(UUID.class).optional().orElse(null);
        notifications.notify(new NotificationService.Request(companyId, recipients, NotificationKind.EMAIL_FAILED,
                "An email to " + who + " couldn't be sent",
                "'" + subject + "' failed after 4 tries. Please pass the details on another way, or check the address.",
                applicationId == null ? null : "/applications/" + applicationId, jobId, applicationId, interviewId));
    }

    private void log(String to, String event, String detail) {
        jdbc.sql("""
                insert into email_log (to_address, provider, event, detail, occurred_at)
                values (:to, :p, cast(:e as email_event_t), :d, :at)
                """)
                .param("to", to).param("p", gateway.providerName()).param("e", event).param("d", detail)
                .param("at", Timestamp.from(clock.instant())).update();
    }

    private String html(String subject, String body) {
        Context context = new Context();
        context.setVariable("subject", subject);
        context.setVariable("paragraphs", Arrays.stream(body.split("\\n\\s*\\n")).map(String::strip).toList());
        context.setVariable("brandColor", brandColor);
        context.setVariable("headingColor", headingColor);
        return templates.process("mail/message", context);
    }

    private static String hex(String value, String fallback) {
        return value != null && value.strip().matches("#[0-9a-fA-F]{6}") ? value.strip() : fallback;
    }
}
