package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.security.ActorContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Tells HR when a candidate hasn't replied by the deadline, and reminds panel members whose Round 3 feedback is
 * still missing a day after the interview. Each alert is sent once.
 */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class InterviewAlertsJob {

    private final JdbcClient jdbc;
    private final NotificationService notifications;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final com.epictalenttrust.hr.mail.Outbox outbox;

    public InterviewAlertsJob(JdbcClient jdbc, NotificationService notifications, TransactionTemplate transaction,
                              Clock clock, com.epictalenttrust.hr.mail.Outbox outbox) {
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void run() {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> transaction.executeWithoutResult(status -> {
            noReply();
            feedbackDue();
            roundsDue();
        }));
    }

    void noReply() {
        record Row(UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID scheduledBy, int round,
                   String name, String job) {
        }
        jdbc.sql("""
                        select i.id, a.id as application_id, j.id as job_id, j.company_id, i.scheduled_by, i.round,
                               c.full_name, j.title
                          from interviews i join applications a on a.id = i.application_id join jobs j on j.id = a.job_id
                          join candidates c on c.id = a.candidate_id
                         where i.status = 'invited' and i.reply_by < :now and a.status = 'active'
                           and not exists (select 1 from notifications n where n.interview_id = i.id and n.kind = 'no_reply'
                                              and n.created_at > coalesce(i.invited_at, i.created_at))
                         limit 200
                        """)
                .param("now", Timestamp.from(clock.instant()))
                .query((rs, row) -> new Row(Rows.uuid(rs, "id"), Rows.uuid(rs, "application_id"), Rows.uuid(rs, "job_id"),
                        Rows.uuid(rs, "company_id"), Rows.uuid(rs, "scheduled_by"), rs.getInt("round"),
                        rs.getString("full_name"), rs.getString("title")))
                .list()
                .forEach(r -> notifications.notify(new NotificationService.Request(r.companyId(),
                        Arrays.asList(r.scheduledBy(), notifications.jobOwner(r.jobId())), NotificationKind.NO_REPLY,
                        r.name() + " hasn't replied to the " + roundLabel(r.jobId(), r.round()) + " invitation",
                        "The reply deadline has passed · " + r.job() + ". Follow up, send a new time or cancel.",
                        "/applications/" + r.applicationId(), r.jobId(), r.applicationId(), r.interviewId())));
    }

    /** A day after a panel interview, panel members who haven't sent their review get one reminder email. */
    void feedbackDue() {
        record Row(UUID interviewId, UUID jobId, UUID companyId, String memberName, String email, String candidate,
                   String job, String roundName, int round) {
        }
        jdbc.sql("""
                        select i.id, j.id as job_id, j.company_id, m.full_name as member, m.email, c.full_name, j.title,
                               jr.name as round_name, i.round
                          from interviews i join panel_members m on m.interview_id = i.id
                          join applications a on a.id = i.application_id join jobs j on j.id = a.job_id
                          join candidates c on c.id = a.candidate_id
                          left join job_rounds jr on jr.job_id = j.id and jr.round = i.round
                         where i.status in ('invited', 'confirmed', 'completed') and a.status = 'active'
                           and i.start_at < :cutoff and i.start_at > :oldest
                           and not exists (select 1 from panel_reviews r where r.panel_member_id = m.id and r.submitted_at is not null)
                           and not exists (select 1 from stage_decisions d where d.application_id = a.id
                                             and d.stage::text = 'round' || i.round and d.decision <> 'hold')
                           and not exists (select 1 from email_outbox o where o.interview_id = i.id
                                             and o.purpose = 'panel_reminder' and lower(o.to_address) = lower(m.email))
                         limit 200
                        """)
                .param("cutoff", Timestamp.from(clock.instant().minus(Duration.ofHours(24))))
                .param("oldest", Timestamp.from(clock.instant().minus(Duration.ofDays(3))))
                .query((rs, row) -> new Row(Rows.uuid(rs, "id"), Rows.uuid(rs, "job_id"), Rows.uuid(rs, "company_id"),
                        rs.getString("member"), rs.getString("email"), rs.getString("full_name"), rs.getString("title"),
                        rs.getString("round_name"), rs.getInt("round")))
                .list()
                .forEach(r -> {
                    String first = r.memberName().strip().split("\\s+")[0];
                    String round = com.epictalenttrust.hr.message.MessageTemplates.label(r.roundName(), r.round());
                    outbox.queue(new com.epictalenttrust.hr.mail.Outbox.Email(r.companyId(), r.jobId(), r.interviewId(),
                            "panel_reminder", r.email(), r.memberName(),
                            "Reminder: your review of " + r.candidate() + " (" + round + ")",
                            """
                            Hello %s,

                            HR is waiting for your review of %s (%s, %s) before deciding the next step.

                            Please sign in with the panel login, enter your employee ID and the interview code from your invitation email, and send your review. Reviews close two days after the interview.

                            Thank you""".formatted(first, r.candidate(), round, r.job())));
                });
    }

    private String roundLabel(UUID jobId, int round) {
        String name = jdbc.sql("select name from job_rounds where job_id = :j and round = :r")
                .param("j", jobId).param("r", round).query(String.class).optional().orElse(null);
        return com.epictalenttrust.hr.message.MessageTemplates.label(name, round);
    }

    /**
     * Rounds with a target time: the day before a candidate's round is due (and isn't decided yet), the job's owner
     * and whoever is covering for them are reminded once.
     */
    void roundsDue() {
        record Row(UUID applicationId, UUID jobId, UUID companyId, UUID owner, String name, String job, String round,
                   java.time.Instant due) {
        }
        com.epictalenttrust.hr.application.PipelineQueries.bindSystem(jdbc.sql(
                        com.epictalenttrust.hr.application.PipelineQueries.PIPELINE + """
                        select p.application_id, p.job_id, p.company_id, p.job_owner_id, p.full_name, p.job_title,
                               p.round_name, p.current_round, p.due_at
                          from pipeline p
                          join applications a on a.id = p.application_id
                         where p.due_at is not null and p.due_at < :soon and a.due_reminder_sent_at is null
                           and p.next_action in ('schedule_round', 'decide_round', 'waiting')
                         limit 200
                        """))
                .param("soon", Timestamp.from(clock.instant().plus(Duration.ofDays(1))))
                .query((rs, row) -> new Row(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "job_id"),
                        Rows.uuid(rs, "company_id"), Rows.uuid(rs, "job_owner_id"), rs.getString("full_name"),
                        rs.getString("job_title"),
                        com.epictalenttrust.hr.message.MessageTemplates.label(rs.getString("round_name"), rs.getInt("current_round")),
                        Rows.instant(rs, "due_at")))
                .list()
                .forEach(r -> {
                    boolean overdue = r.due().isBefore(clock.instant());
                    notifications.notify(new NotificationService.Request(r.companyId(),
                            java.util.List.of(r.owner() == null ? notifications.jobOwner(r.jobId()) : r.owner()),
                            NotificationKind.ROUND_DUE,
                            (overdue ? r.round() + " is overdue for " : r.round() + " is due tomorrow for ") + r.name(),
                            r.job() + " · the round's target time " + (overdue ? "has passed." : "ends within a day."),
                            "/applications/" + r.applicationId(), r.jobId(), r.applicationId(), null));
                    jdbc.sql("update applications set due_reminder_sent_at = now() where id = :id")
                            .param("id", r.applicationId()).update();
                });
    }
}
