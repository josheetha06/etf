package com.epictalenttrust.hr.message;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.epictalenttrust.hr.common.Rows.enumOf;
import static com.epictalenttrust.hr.common.Rows.instant;
import static com.epictalenttrust.hr.common.Rows.uuid;

@Component
@Transactional(readOnly = true)
public class MessageQueries {

    public record MessageView(UUID id, UUID applicationId, UUID interviewId, UUID jobId, String candidateName,
                              String jobTitle, SenderKind senderKind, String senderName, MessageDirection direction,
                              String subject, String body, Instant sentAt, Instant readAt, String deliveryStatus) {
    }

    private static final String SELECT = """
            select m.id, m.application_id, m.interview_id, j.id as job_id, c.full_name as candidate_name, j.title,
                   m.sender_kind, m.direction, m.subject, m.body, coalesce(m.sent_at, m.created_at) as sent_at, m.read_at,
                   case m.sender_kind when 'hr_user' then hu.full_name when 'candidate' then c.full_name
                                      when 'system' then 'AI agent' else 'ETT' end as sender_name,
                   case when m.sent_at is null and m.send_attempts = 0 and m.next_attempt_at > now() then 'scheduled'
                        else (select event::text from email_log el where el.message_id = m.id
                               order by el.occurred_at desc, el.id desc limit 1) end as delivery
              from messages m
              join applications a on a.id = m.application_id
              join jobs j on j.id = a.job_id
              join candidates c on c.id = a.candidate_id
              left join hr_users hu on m.sender_kind = 'hr_user' and hu.id = m.sender_id
            """;

    private final JdbcClient jdbc;

    public MessageQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<MessageView> folder(UUID companyId, MessageDirection direction) {
        return jdbc.sql(SELECT +  " where j.company_id = :companyId and m.direction = :direction and m.cancelled_at is null"
                        + " order by coalesce(m.sent_at, m.created_at) desc limit 300")
                .param("companyId", companyId)
                .param("direction", direction.name().toLowerCase())
                .query(this::row)
                .list();
    }

    public List<MessageView> forApplication(UUID applicationId) {
        return jdbc.sql(SELECT +  " where m.application_id = :id and m.cancelled_at is null order by coalesce(m.sent_at, m.created_at) desc")
                .param("id", applicationId)
                .query(this::row)
                .list();
    }

    public Optional<UUID> applicationOf(UUID messageId) {
        return jdbc.sql("select application_id from messages where id = :id").param("id", messageId)
                .query(UUID.class).optional();
    }

    public int unreadCount(UUID companyId) {
        Integer count = jdbc.sql("""
                        select count(*) from messages m join applications a on a.id = m.application_id
                          join jobs j on j.id = a.job_id
                         where j.company_id = :companyId and m.direction = 'inbound' and m.read_at is null
                        """)
                .param("companyId", companyId).query(Integer.class).single();
        return count == null ? 0 : count;
    }

    private MessageView row(ResultSet rs, int rowNum) throws SQLException {
        return new MessageView(uuid(rs, "id"), uuid(rs, "application_id"), uuid(rs, "interview_id"), uuid(rs, "job_id"),
                rs.getString("candidate_name"), rs.getString("title"), enumOf(rs, "sender_kind", SenderKind.class),
                rs.getString("sender_name"), enumOf(rs, "direction", MessageDirection.class), rs.getString("subject"),
                rs.getString("body"), instant(rs, "sent_at"), instant(rs, "read_at"), rs.getString("delivery"));
    }
}
