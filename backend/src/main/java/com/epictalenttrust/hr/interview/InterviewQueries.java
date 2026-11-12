package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.job.InterviewMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static com.epictalenttrust.hr.common.Rows.enumOf;
import static com.epictalenttrust.hr.common.Rows.instant;
import static com.epictalenttrust.hr.common.Rows.integer;
import static com.epictalenttrust.hr.common.Rows.uuid;

@Component
@Transactional(readOnly = true)
public class InterviewQueries {

    public enum View { UPCOMING, REQUESTS, COMPLETED, DRAFTS }

    private static final String SELECT = """
            select i.id, i.application_id, j.id as job_id, j.title as job_title, c.full_name, c.country, i.round, i.mode,
                   i.start_at, i.candidate_tz, i.duration_min, i.status, i.reply_by, i.invited_at,
                   rr.id as pending_request_id, u.full_name as scheduled_by_name, i.meeting_link, i.arrive_by,
                   pc.name as centre_name, concat_ws(', ', pc.address, pc.city) as centre_address,
                   pa.full_name as assessor_name, i.completed_at, jr.name as round_name, jr.kind as round_kind,
                   i.venue, i.panel_code_enc, i.centre_note,
                   (select count(*) from panel_members p where p.interview_id = i.id) as panel_size,
                   (select count(*) from panel_reviews f where f.interview_id = i.id and f.submitted_at is not null)
                       as feedback_count
              from interviews i
              join applications a on a.id = i.application_id
              join jobs j on j.id = a.job_id
              left join job_rounds jr on jr.job_id = j.id and jr.round = i.round
              join candidates c on c.id = a.candidate_id
              left join hr_users u on u.id = i.scheduled_by
              left join centre_slots cs on cs.id = i.slot_id
              left join partner_centres pc on pc.id = cs.centre_id
              left join partner_assessors pa on pa.id = i.assessor_id
              left join lateral (select id from reschedule_requests where interview_id = i.id and status = 'pending'
                                  order by created_at desc limit 1) rr on true
            """;

    private final JdbcClient jdbc;
    private final com.epictalenttrust.hr.security.Access access;
    private final com.epictalenttrust.hr.security.SecretCipher cipher;
    private final com.epictalenttrust.hr.common.ChangeLog changeLog;
    private final com.epictalenttrust.hr.panel.PanelWindow window;

    public InterviewQueries(JdbcClient jdbc, com.epictalenttrust.hr.security.Access access,
                            com.epictalenttrust.hr.security.SecretCipher cipher,
                            com.epictalenttrust.hr.common.ChangeLog changeLog,
                            com.epictalenttrust.hr.panel.PanelWindow window) {
        this.window = window;
        this.jdbc = jdbc;
        this.access = access;
        this.cipher = cipher;
        this.changeLog = changeLog;
    }

    /** The interviews the signed-in person can see, with who created and last changed each. */
    public List<InterviewRow> list(View view, UUID jobId) {
        String filter = switch (view) {
            case UPCOMING -> "i.status in ('invited', 'confirmed', 'reschedule_requested') and i.start_at >= now() - interval '2 hours'";
            case REQUESTS -> "rr.id is not null";
            case COMPLETED -> "(i.status in ('completed', 'no_show', 'cancelled') or (i.status <> 'draft' and i.start_at < now() - interval '2 hours'))";
            case DRAFTS -> "i.status = 'draft'";
        };
        String order = view == View.COMPLETED ? "i.start_at desc" : "i.start_at asc";
        List<InterviewRow> rows = access.bind(jdbc.sql(SELECT + """
                         where j.company_id = :companyId
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                           and (cast(:jobId as uuid) is null or j.id = :jobId) and """ + " " + filter
                        + " order by " + order + " limit 500"))
                .param("jobId", jobId)
                .query(this::row)
                .list();
        var changes = changeLog.forRecords("interviews", rows.stream().map(InterviewRow::id).toList());
        return rows.stream().map(r -> r.withExtras(r.panelCode(), r.panel(), changes.get(r.id()))).toList();
    }

    public Optional<InterviewRow> byId(UUID interviewId) {
        return jdbc.sql(SELECT + " where i.id = :id").param("id", interviewId).query(this::row).optional()
                .map(this::withPanel);
    }

    /** Every interview of one application, with the panel and the interview code for panel rounds. */
    public List<InterviewRow> forApplication(UUID applicationId) {
        return jdbc.sql(SELECT + " where i.application_id = :id order by i.round, i.created_at")
                .param("id", applicationId).query(this::row).list().stream().map(this::withPanel).toList();
    }

    public List<InterviewDtos.PanelMemberRef> panel(UUID interviewId) {
        return jdbc.sql("""
                        select m.id, m.employee_id, m.full_name, m.email,
                               r.submitted_at is not null as submitted, r.id is not null as started
                          from panel_members m left join panel_reviews r on r.panel_member_id = m.id
                         where m.interview_id = :i order by m.created_at, m.full_name
                        """)
                .param("i", interviewId)
                .query((rs, n) -> new InterviewDtos.PanelMemberRef(uuid(rs, "id"), rs.getString("employee_id"),
                        rs.getString("full_name"), rs.getString("email"), rs.getBoolean("submitted"),
                        rs.getBoolean("started")))
                .list();
    }

    private InterviewRow withPanel(InterviewRow row) {
        if (row.roundKind() != com.epictalenttrust.hr.job.RoundKind.PANEL) {
            return row.withExtras(null, List.of(), changeLog.forRecord("interviews", row.id()));
        }
        return row.withExtras(row.panelCode(), panel(row.id()), changeLog.forRecord("interviews", row.id()));
    }

    private InterviewRow row(ResultSet rs, int rowNum) throws SQLException {
        String mode = rs.getString("mode");
        return new InterviewRow(uuid(rs, "id"), uuid(rs, "application_id"), uuid(rs, "job_id"), rs.getString("job_title"),
                rs.getString("full_name"), rs.getString("country"), rs.getInt("round"),
                mode == null ? null : InterviewMode.valueOf(mode.toUpperCase(Locale.ROOT)), instant(rs, "start_at"),
                rs.getString("candidate_tz"), integer(rs, "duration_min"), enumOf(rs, "status", InterviewStatus.class),
                instant(rs, "reply_by"), instant(rs, "invited_at"), uuid(rs, "pending_request_id"),
                rs.getString("scheduled_by_name"), rs.getString("meeting_link"), instant(rs, "arrive_by"),
                rs.getString("centre_name"), blankToNull(rs.getString("centre_address")), rs.getString("assessor_name"),
                rs.getInt("panel_size"), rs.getInt("feedback_count"), instant(rs, "completed_at"),
                rs.getString("round_name"), enumOf(rs, "round_kind", com.epictalenttrust.hr.job.RoundKind.class),
                rs.getString("venue"), decrypt(rs.getString("panel_code_enc")), List.of(), null,
                rs.getString("centre_note"),
                rs.getString("panel_code_enc") == null || instant(rs, "start_at") == null ? null
                        : window.opensAt(instant(rs, "start_at")),
                rs.getString("panel_code_enc") == null || instant(rs, "start_at") == null ? null
                        : window.closesAt(instant(rs, "start_at"), integer(rs, "duration_min")));
    }

    private String decrypt(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            return cipher.decrypt(encrypted);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
