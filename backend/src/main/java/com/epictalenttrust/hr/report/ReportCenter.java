package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.PipelineQueries;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Reports page: every report HR receives (the shortlist, and each round's report: AI interview, partner assessor
 * or panel) in one list, split into "To review" and "Reviewed". The same reports are also on each candidate's page.
 */
@Service
public class ReportCenter {

    public enum View { TO_REVIEW, REVIEWED, ALL }

    public record ReportRow(UUID applicationId, UUID jobId, String jobTitle, String jobRef, String candidateName,
                            String candidateRef, String type, int round, String roundName, Instant readyAt, Integer score,
                            String recommendation, boolean sample, int redFlags, int redFlagsOpen, boolean toReview,
                            String decision, Instant decidedAt, boolean recheckRequested) {
    }

    public record Counts(int shortlisting, int aiInterview, int partner, int panel, int total) {
    }

    /** Shortlisting from the pipeline, plus one row per interview whose report is in. */
    private static final String REPORTS = PipelineQueries.PIPELINE + """
            , reports as (
              select p.application_id, p.job_id, p.job_title, p.job_ref, p.full_name, p.candidate_ref, p.red_total,
                     p.red_open, 'shortlisting' as type, 0 as round, null::text as round_name, p.screened_at as ready_at,
                     p.match_score as score, p.suggestion::text as recommendation, false as sample,
                     p.next_action in ('decide_shortlist', 'review_flags') as to_review,
                     p.shortlist_decision::text as decision, p.shortlist_decided_at as decided_at, false as recheck
                from pipeline p where p.screening_id is not null
              union all
              select p.application_id, p.job_id, p.job_title, p.job_ref, p.full_name, p.candidate_ref, p.red_total,
                     p.red_open, jr.kind::text, i.round, jr.name,
                     case jr.kind when 'ai_interview' then ar.released_at when 'partner' then pa.submitted_at
                                  else pr.last_submitted end,
                     case jr.kind when 'ai_interview' then ar.final_overall when 'partner' then pa.score
                                  else round(pr.avg_score * 20)::int end,
                     case jr.kind when 'ai_interview' then ar.recommendation::text
                                  when 'partner' then pa.recommendation::text else pr.recommendation end,
                     case jr.kind when 'ai_interview' then coalesce(ar.is_sample, false)
                                  when 'partner' then coalesce(pa.is_sample, false) else coalesce(pr.sample, false) end,
                     p.next_action = 'decide_round' and p.round_interview_id = i.id,
                     sd.decision::text, sd.created_at,
                     jr.kind = 'ai_interview' and ar.recheck_requested_at is not null and ar.status <> 'released'
                from pipeline p
                join interviews i on i.application_id = p.application_id and i.status not in ('cancelled', 'draft')
                join job_rounds jr on jr.job_id = p.job_id and jr.round = i.round
                left join round1_reports ar on jr.kind = 'ai_interview' and ar.interview_id = i.id
                left join lateral (select submitted_at, score, recommendation, is_sample from round2_assessments
                                    where jr.kind = 'partner' and interview_id = i.id and submitted_at is not null
                                    order by submitted_at desc limit 1) pa on true
                left join lateral (select count(*) filter (where submitted_at is not null) as n,
                                          max(submitted_at) as last_submitted, bool_or(is_sample) as sample,
                                          avg(overall_score) filter (where submitted_at is not null) as avg_score,
                                          mode() within group (order by case when recommendation in ('strong_hire', 'hire')
                                                                             then 'advance' else 'reject' end)
                                              filter (where submitted_at is not null) as recommendation
                                     from panel_reviews where jr.kind = 'panel' and interview_id = i.id) pr on true
                left join lateral (select decision, created_at from stage_decisions
                                    where application_id = p.application_id and stage::text = 'round' || i.round
                                    order by created_at desc limit 1) sd on true
               where (jr.kind = 'ai_interview' and (ar.status = 'released' or ar.recheck_requested_at is not null))
                  or (jr.kind = 'partner' and pa.submitted_at is not null)
                  or (jr.kind = 'panel' and coalesce(pr.n, 0) > 0)
            )
            """;

    private final Tenancy tenancy;
    private final com.epictalenttrust.hr.security.Access access;
    private final ActivityService activity;
    private final JdbcClient jdbc;
    private final Clock clock;

    public ReportCenter(Tenancy tenancy, com.epictalenttrust.hr.security.Access access, ActivityService activity,
                        JdbcClient jdbc, Clock clock) {
        this.access = access;
        this.tenancy = tenancy;
        this.activity = activity;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ReportRow> list(View view, String type, UUID jobId, boolean flaggedOnly) {
        if (jobId != null) {
            tenancy.job(jobId);
        }
        String filter = switch (view) {
            case TO_REVIEW -> "to_review";
            case REVIEWED -> "not to_review and decision is not null";
            case ALL -> "true";
        };
        return access.bind(jdbc.sql(REPORTS + """
                        select * from reports
                         where %s
                           and (cast(:type as text) is null or type = :type)
                           and (cast(:jobId as uuid) is null or job_id = :jobId)
                           and (not :flagged or red_total > 0)
                         order by to_review desc, ready_at %s nulls last
                         limit 500
                        """.formatted(filter, view == View.TO_REVIEW ? "asc" : "desc")))
                .param("type", normaliseType(type)).param("jobId", jobId)
                .param("flagged", flaggedOnly)
                .query((rs, row) -> new ReportRow(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "job_id"),
                        rs.getString("job_title"), rs.getString("job_ref"), rs.getString("full_name"),
                        rs.getString("candidate_ref"), rs.getString("type"), rs.getInt("round"),
                        rs.getString("round_name"), Rows.instant(rs, "ready_at"), Rows.integer(rs, "score"), rs.getString("recommendation"),
                        rs.getBoolean("sample"), rs.getInt("red_total"), rs.getInt("red_open"),
                        rs.getBoolean("to_review"), rs.getString("decision"), Rows.instant(rs, "decided_at"),
                        rs.getBoolean("recheck")))
                .list();
    }

    @Transactional(readOnly = true)
    public Counts counts() {
        Map<String, Integer> byType = new java.util.HashMap<>();
        access.bind(jdbc.sql(REPORTS + "select type, count(*) as n from reports where to_review group by type"))
                .query((rs, row) -> byType.put(rs.getString("type"), rs.getInt("n")))
                .list();
        int s = byType.getOrDefault("shortlisting", 0);
        int ai = byType.getOrDefault("ai_interview", 0);
        int partner = byType.getOrDefault("partner", 0);
        int panel = byType.getOrDefault("panel", 0);
        return new Counts(s, ai, partner, panel, s + ai + partner + panel);
    }

    /**
     * HR asks ETT to look at an AI interview's identity and surroundings checks again. The report is taken back for
     * review; ETT re-releases it. Sample reports are re-released straight away.
     */
    @Transactional
    public void requestAiRecheck(UUID applicationId, String note) {
        JobApplication application = tenancy.application(applicationId);
        String trimmed = note == null ? "" : note.strip();
        if (trimmed.length() < 5) {
            throw ApiException.badRequest("reason_required", "Tell ETT what to re-check (at least 5 characters).");
        }
        record Report(UUID id, UUID interviewId, boolean sample, int round) {
        }
        Report report = jdbc.sql("""
                        select r.id, r.interview_id, r.is_sample, i.round from round1_reports r
                          join interviews i on i.id = r.interview_id
                         where i.application_id = :a and r.status = 'released' and i.status <> 'cancelled'
                         order by r.released_at desc limit 1
                        """)
                .param("a", applicationId)
                .query((rs, row) -> new Report(Rows.uuid(rs, "id"), Rows.uuid(rs, "interview_id"), rs.getBoolean("is_sample"),
                        rs.getInt("round")))
                .optional()
                .orElseThrow(() -> ApiException.conflict("report_not_ready", "There's no released AI interview report to re-check."));
        boolean decided = jdbc.sql("""
                        select exists (select 1 from stage_decisions where application_id = :a
                                          and stage::text = 'round' || :r and decision <> 'hold')
                        """)
                .param("a", applicationId).param("r", report.round()).query(Boolean.class).single();
        if (decided || !application.isActive()) {
            throw ApiException.conflict("already_decided", "A decision has already been made for this round.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        if (report.sample()) {
            jdbc.sql("""
                    update round1_reports set recheck_requested_at = :now, recheck_note = :note, released_at = :now,
                           reviewer_notes = concat_ws(E'\\n\\n', reviewer_notes,
                               'Re-checked by ETT (sample): the recording was reviewed again and the findings stand.')
                     where id = :id
                    """)
                    .param("now", now).param("note", trimmed).param("id", report.id()).update();
        } else {
            jdbc.sql("""
                    update round1_reports set recheck_requested_at = :now, recheck_note = :note, status = 'in_review'
                     where id = :id
                    """)
                    .param("now", now).param("note", trimmed).param("id", report.id()).update();
        }
        jdbc.sql("""
                insert into report_reviews (application_id, interview_id, report_type, outcome, note, reviewed_by, created_by)
                values (:a, :i, 'round' || :r, 'recheck', :n, :u, :u)
                """)
                .param("a", applicationId).param("i", report.interviewId()).param("n", trimmed)
                .param("r", report.round()).param("u", CurrentUser.id()).update();
        activity.record("report.recheck_requested", "application", applicationId);
    }

    private static String normaliseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.strip().toLowerCase(Locale.ROOT);
        if (!List.of("shortlisting", "ai_interview", "partner", "panel").contains(t)) {
            throw ApiException.badRequest("invalid_type", "Report type is shortlisting, ai_interview, partner or panel.");
        }
        return t;
    }
}
