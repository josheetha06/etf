package com.epictalenttrust.hr.message;

import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.mail.MailGateway;
import com.epictalenttrust.hr.security.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Sends queued candidate emails after the change that created them has been saved. */
@Component
public class MailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailDispatcher.class);
    private static final Pattern HEX_COLOUR = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final int[] RETRIES = {2, 10, 30};

    private final MessageRepository messages;
    private final CandidateRepository candidates;
    private final MailGateway gateway;
    private final TemplateEngine templates;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String brandColor;
    private final String headingColor;
    private final com.epictalenttrust.hr.notification.NotificationService notifications;

    public MailDispatcher(MessageRepository messages, CandidateRepository candidates, MailGateway gateway,
                          TemplateEngine templates, JdbcClient jdbc, PlatformTransactionManager transactionManager,
                          Clock clock, EttProperties properties,
                          com.epictalenttrust.hr.notification.NotificationService notifications) {
        this.notifications = notifications;
        this.messages = messages;
        this.candidates = candidates;
        this.gateway = gateway;
        this.templates = templates;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.brandColor = hexOr(properties.mail().brandColor(), "#0a7476");
        this.headingColor = hexOr(properties.mail().headingColor(), "#0b2545");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailRequested(OutboundEmailRequested event) {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> deliver(event));
    }

    private void deliver(OutboundEmailRequested event) {
        Message message = messages.findById(event.messageId()).orElse(null);
        if (message == null || message.getSentAt() != null) {
            return;
        }
        boolean cancelled = jdbc.sql("select cancelled_at is not null from messages where id = :id")
                .param("id", message.getId()).query(Boolean.class).single();
        if (cancelled) {
            return;
        }
        String to = jdbc.sql("""
                        select c.email from messages m join applications a on a.id = m.application_id
                          join candidates c on c.id = a.candidate_id where m.id = :id
                        """)
                .param("id", message.getId()).query(String.class).optional().orElse(null);
        try {
            if (to == null) {
                throw new IllegalStateException("Candidate has no email address");
            }
            gateway.send(to, message.getSubject(), message.getBody(), renderHtml(message));
            transaction.executeWithoutResult(status -> {
                messages.findById(message.getId()).ifPresent(m -> m.markSent(clock.instant()));
                logEvent(message, to, "sent", null);
            });
        } catch (Exception ex) {
            log.warn("Email {} to {} failed: {}", message.getId(), to, ex.getMessage());
            transaction.executeWithoutResult(status -> {
                logEvent(message, to, "failed", ex.getMessage());
                scheduleRetry(message.getId(), ex.getMessage());
            });
        }
    }

    /** Tries a failed candidate email again after 2, 10 and 30 minutes, then tells HR. */
    private void scheduleRetry(java.util.UUID messageId, String reason) {
        int attempts = jdbc.sql("update messages set send_attempts = send_attempts + 1 where id = :id returning send_attempts")
                .param("id", messageId).query(Integer.class).single();
        if (attempts > RETRIES.length) {
            jdbc.sql("update messages set failed_at = now(), failure_reason = :r where id = :id")
                    .param("r", reason).param("id", messageId).update();
            record Ref(java.util.UUID companyId, java.util.UUID jobId, java.util.UUID applicationId, String name,
                       String subject) {
            }
            Ref ref = jdbc.sql("""
                            select j.company_id, j.id as job_id, a.id as application_id, c.full_name, m.subject
                              from messages m join applications a on a.id = m.application_id
                              join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                             where m.id = :id
                            """)
                    .param("id", messageId)
                    .query((rs, n) -> new Ref(com.epictalenttrust.hr.common.Rows.uuid(rs, "company_id"),
                            com.epictalenttrust.hr.common.Rows.uuid(rs, "job_id"),
                            com.epictalenttrust.hr.common.Rows.uuid(rs, "application_id"), rs.getString("full_name"),
                            rs.getString("subject")))
                    .single();
            notifications.notify(new com.epictalenttrust.hr.notification.NotificationService.Request(ref.companyId(),
                    java.util.List.of(notifications.jobOwner(ref.jobId())),
                    com.epictalenttrust.hr.notification.NotificationKind.EMAIL_FAILED,
                    "An email to " + ref.name() + " couldn't be sent",
                    "'" + ref.subject() + "' failed after 4 tries. Check their email address, or contact them another way.",
                    "/applications/" + ref.applicationId(), ref.jobId(), ref.applicationId(), null));
        } else {
            jdbc.sql("update messages set next_attempt_at = now() + make_interval(mins => :m), failure_reason = :r where id = :id")
                    .param("m", RETRIES[attempts - 1]).param("r", reason).param("id", messageId).update();
        }
    }

    /** Sends again any candidate email whose retry time has come. */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void retryDue() {
        jdbc.sql("""
                        select id from messages
                         where direction = 'outbound' and sent_at is null and failed_at is null and cancelled_at is null
                           and next_attempt_at is not null and next_attempt_at <= now()
                         order by next_attempt_at limit 50
                        """)
                .query(java.util.UUID.class).list()
                .forEach(id -> ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> deliver(new OutboundEmailRequested(id))));
    }

    private void logEvent(Message message, String to, String eventName, String detail) {
        jdbc.sql("""
                insert into email_log (message_id, to_address, provider, event, detail)
                values (:id, :to, :provider, :event, :detail)
                """)
                .param("id", message.getId())
                .param("to", to)
                .param("provider", gateway.providerName())
                .param("event", eventName)
                .param("detail", detail)
                .update();
    }

    private String renderHtml(Message message) {
        List<String> paragraphs = Arrays.stream(message.getBody().split("\\n\\s*\\n")).map(String::strip).toList();
        Context context = new Context();
        context.setVariable("subject", message.getSubject());
        context.setVariable("paragraphs", paragraphs);
        context.setVariable("brandColor", brandColor);
        context.setVariable("headingColor", headingColor);
        return templates.process("mail/message", context);
    }

    /** Only a plain hex colour may reach the email's inline styles. */
    static String hexOr(String value, String fallback) {
        return value != null && HEX_COLOUR.matcher(value.strip()).matches() ? value.strip() : fallback;
    }
}
