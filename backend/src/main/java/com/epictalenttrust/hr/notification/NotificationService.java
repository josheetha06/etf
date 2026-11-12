package com.epictalenttrust.hr.notification;

import com.epictalenttrust.hr.common.Rows;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The bell: notifications for HR users, plus email alerts sent after the change that caused them is saved
 * (instantly, in a daily summary, or not at all, per user).
 */
@Service
public class NotificationService {

    /** Who to tell and what about. Links are paths in the portal, e.g. /applications/{id}. */
    public record Request(UUID companyId, Collection<UUID> recipients, NotificationKind kind, String title, String body,
                         String link, UUID jobId, UUID applicationId, UUID interviewId) {
    }

    public record View(UUID id, String kind, String title, String body, String link, UUID jobId, UUID applicationId,
                       Instant createdAt, Instant readAt) {
    }

    /** Published after notifications are saved; the mailer sends instant alerts once the transaction commits. */
    public record Created(List<UUID> ids) {
    }

    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public NotificationService(JdbcClient jdbc, ApplicationEventPublisher events, Clock clock) {
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<UUID> notify(Request request) {
        Set<UUID> recipients = new LinkedHashSet<>();
        request.recipients().stream().filter(Objects::nonNull).forEach(recipients::add);
        // Anyone on leave: the colleague covering for them gets the same notification.
        if (!recipients.isEmpty()) {
            recipients.addAll(jdbc.sql("""
                            select lc.cover_user_id from leave_cover lc join companies co on co.id = lc.company_id
                             where lc.user_id in (:people) and lc.ended_early_at is null
                               and (now() at time zone co.time_zone)::date between lc.starts_on and lc.ends_on
                            """)
                    .param("people", recipients).query(UUID.class).list());
        }
        List<UUID> ids = new ArrayList<>();
        for (UUID recipient : recipients) {
            UUID id = jdbc.sql("""
                            insert into notifications (company_id, recipient_id, kind, title, body, link, job_id,
                                                       application_id, interview_id, created_at)
                            select :company, u.id, :kind, :title, :body, :link, :job, :application, :interview, :at
                              from hr_users u where u.id = :recipient and u.company_id = :company and u.status <> 'disabled'
                            returning id
                            """)
                    .param("company", request.companyId())
                    .param("recipient", recipient)
                    .param("kind", request.kind().name().toLowerCase(Locale.ROOT))
                    .param("title", request.title())
                    .param("body", request.body())
                    .param("link", request.link())
                    .param("job", request.jobId())
                    .param("application", request.applicationId())
                    .param("interview", request.interviewId())
                    .param("at", java.sql.Timestamp.from(clock.instant()))
                    .query(UUID.class).optional().orElse(null);
            if (id != null) {
                ids.add(id);
            }
        }
        if (!ids.isEmpty()) {
            events.publishEvent(new Created(ids));
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID recipient, boolean unreadOnly, int limit) {
        return jdbc.sql("""
                        select id, kind, title, body, link, job_id, application_id, created_at, read_at
                          from notifications where recipient_id = :r and (:all or read_at is null)
                         order by created_at desc limit :limit
                        """)
                .param("r", recipient).param("all", !unreadOnly).param("limit", Math.min(Math.max(limit, 1), 100))
                .query((rs, row) -> new View(Rows.uuid(rs, "id"), rs.getString("kind"), rs.getString("title"),
                        rs.getString("body"), rs.getString("link"), Rows.uuid(rs, "job_id"),
                        Rows.uuid(rs, "application_id"), Rows.instant(rs, "created_at"), Rows.instant(rs, "read_at")))
                .list();
    }

    @Transactional(readOnly = true)
    public int unreadCount(UUID recipient) {
        return jdbc.sql("select count(*) from notifications where recipient_id = :r and read_at is null")
                .param("r", recipient).query(Integer.class).single();
    }

    @Transactional
    public void markRead(UUID recipient, UUID id) {
        jdbc.sql("update notifications set read_at = coalesce(read_at, :at) where id = :id and recipient_id = :r")
                .param("at", java.sql.Timestamp.from(clock.instant())).param("id", id).param("r", recipient).update();
    }

    @Transactional
    public void markAllRead(UUID recipient) {
        jdbc.sql("update notifications set read_at = :at where recipient_id = :r and read_at is null")
                .param("at", java.sql.Timestamp.from(clock.instant())).param("r", recipient).update();
    }

    /** The HR person who owns the job: they hear about everything in it. */
    @Transactional(readOnly = true)
    public UUID jobOwner(UUID jobId) {
        return jdbc.sql("select coalesce(owner_id, created_by) from jobs where id = :id").param("id", jobId)
                .query(UUID.class).optional().orElse(null);
    }
}
