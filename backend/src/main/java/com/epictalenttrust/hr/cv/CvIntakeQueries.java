package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.screening.AiSuggestion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Transactional(readOnly = true)
public class CvIntakeQueries {

    /** A CV in the intake list: where it is, why it wasn't added, or who it became. */
    public record UploadRow(UUID id, UUID batchId, String fileName, CvUpload.Status status, String errorCode,
                            String message, UUID applicationId, String candidateName, Integer matchScore,
                            AiSuggestion suggestion, CandidateDetails detected, Instant queuedAt, Instant finishedAt,
                            ExistingCandidate existingCandidate) {
    }

    /** The detected email already belongs to one of this client's candidates. */
    public record ExistingCandidate(UUID candidateId, String candidateRef, String fullName, int jobs) {
    }

    private static final String SELECT = """
            select u.id, u.batch_id, u.file_name, u.status, u.error_code, u.error_message, u.application_id,
                   c.full_name as candidate_name, s.match_score, s.suggestion, u.detected_name, u.detected_email,
                   u.detected_mobile, u.detected_city, u.detected_country, u.queued_at, u.finished_at, j.company_id
              from cv_uploads u
              join jobs j on j.id = u.job_id
              left join applications a on a.id = u.application_id
              left join candidates c on c.id = a.candidate_id
              left join lateral (select match_score, suggestion from ai_screenings where application_id = a.id
                                  order by created_at desc limit 1) s on true
            """;

    private final JdbcClient jdbc;

    public CvIntakeQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<UploadRow> forJob(UUID jobId, UUID batchId, boolean openOnly) {
        return jdbc.sql(SELECT + """
                         where u.job_id = :job and (cast(:batch as uuid) is null or u.batch_id = :batch)
                           and (not :open or u.status in ('queued', 'reading', 'needs_details'))
                         order by u.queued_at desc, u.file_name limit 500
                        """)
                .param("job", jobId).param("batch", batchId).param("open", openOnly)
                .query((rs, row) -> map(rs))
                .list();
    }

    public List<UploadRow> byIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " where u.id in (:ids) order by u.file_name")
                .param("ids", ids).query((rs, row) -> map(rs)).list();
    }

    public Optional<UploadRow> byId(UUID id) {
        return byIds(List.of(id)).stream().findFirst();
    }

    private UploadRow map(java.sql.ResultSet rs) throws java.sql.SQLException {
        CandidateDetails detected = new CandidateDetails(rs.getString("detected_name"), rs.getString("detected_email"),
                rs.getString("detected_mobile"), rs.getString("detected_city"), rs.getString("detected_country"));
        CvUpload.Status status = Rows.enumOf(rs, "status", CvUpload.Status.class);
        ExistingCandidate existing = status == CvUpload.Status.NEEDS_DETAILS && detected.email() != null
                ? existing(Rows.uuid(rs, "company_id"), detected.email()).orElse(null) : null;
        return new UploadRow(Rows.uuid(rs, "id"), Rows.uuid(rs, "batch_id"), rs.getString("file_name"), status,
                rs.getString("error_code"), rs.getString("error_message"), Rows.uuid(rs, "application_id"),
                rs.getString("candidate_name"), Rows.integer(rs, "match_score"),
                Rows.enumOf(rs, "suggestion", AiSuggestion.class), detected, Rows.instant(rs, "queued_at"),
                Rows.instant(rs, "finished_at"), existing);
    }

    /** Only candidates already in this client's jobs; other clients' candidates are never revealed. */
    private Optional<ExistingCandidate> existing(UUID companyId, String email) {
        return jdbc.sql("""
                        select c.id, c.candidate_ref, c.full_name, count(a.id) as jobs
                          from candidates c join applications a on a.candidate_id = c.id
                          join jobs j on j.id = a.job_id and j.company_id = :company
                         where lower(c.email) = lower(:email)
                         group by c.id, c.candidate_ref, c.full_name
                        """)
                .param("company", companyId).param("email", email)
                .query((rs, row) -> new ExistingCandidate(Rows.uuid(rs, "id"), rs.getString("candidate_ref"),
                        rs.getString("full_name"), rs.getInt("jobs")))
                .optional();
    }
}
