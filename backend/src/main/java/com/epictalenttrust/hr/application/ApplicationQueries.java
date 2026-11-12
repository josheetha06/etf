package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.application.ApplicationDtos.ApplicationRow;
import com.epictalenttrust.hr.application.ApplicationDtos.DecisionView;
import com.epictalenttrust.hr.application.ApplicationDtos.EvidenceView;
import com.epictalenttrust.hr.application.ApplicationDtos.FlagView;
import com.epictalenttrust.hr.application.ApplicationDtos.ScreeningView;
import com.epictalenttrust.hr.job.CriterionKind;
import com.epictalenttrust.hr.screening.AiSuggestion;
import com.epictalenttrust.hr.screening.EvidenceStrength;
import com.epictalenttrust.hr.shortlist.ShortlistDecisionType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.epictalenttrust.hr.common.Rows.enumOf;
import static com.epictalenttrust.hr.common.Rows.instant;
import static com.epictalenttrust.hr.common.Rows.integer;
import static com.epictalenttrust.hr.common.Rows.uuid;

/** Read models for CV lists, the AI shortlist and match evidence. Callers check tenancy first. */
@Component
@Transactional(readOnly = true)
public class ApplicationQueries {

    private final JdbcClient jdbc;

    public ApplicationQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ApplicationRow> forJob(UUID jobId) {
        return jdbc.sql(PipelineQueries.PIPELINE + """
                        select a.id, a.candidate_id, c.candidate_ref, c.full_name, c.city, c.country, a.cv_status, a.source,
                               a.source_detail, a.created_at, a.stage, a.status,
                               s.match_score, s.suggestion, s.experience_years, s.certifications_found, s.summary, s.reason,
                               s.criteria_version,
                               coalesce(e.must_hit, 0) as must_hit, coalesce(e.must_total, 0) as must_total,
                               coalesce(e.must_backed, 0) as must_backed,
                               coalesce(f.red, 0) as red, coalesce(f.red_open, 0) as red_open, coalesce(f.checks, 0) as checks,
                               d.decision, d.decided_at, p.next_action,
                               p.location_text, p.location_place, p.location_district, p.location_region, p.location_pin, p.location_source, p.location_placed, p.ai_round_ahead
                          from applications a
                          join candidates c on c.id = a.candidate_id
                          join pipeline p on p.application_id = a.id
                          left join lateral (select * from ai_screenings where application_id = a.id
                                              order by created_at desc limit 1) s on true
                          left join lateral (select count(*) filter (where me.matched) as must_hit, count(*) as must_total,
                                                    count(*) filter (where me.strength in ('recent_role', 'older_role')) as must_backed
                                               from match_evidence me join job_criteria jc on jc.id = me.criterion_id
                                              where me.screening_id = s.id and jc.kind = 'must_have') e on true
                          left join lateral (select count(*) filter (where severity = 'red') as red,
                                                    count(*) filter (where severity = 'red' and review_outcome is null) as red_open,
                                                    count(*) filter (where severity = 'check') as checks
                                               from screening_flags where screening_id = s.id) f on true
                          left join lateral (select decision, decided_at from shortlist_decisions where application_id = a.id
                                              order by created_at desc limit 1) d on true
                         where a.job_id = :jobId
                         order by s.match_score desc nulls last, c.full_name
                        """)
                .param("jobId", jobId)
                .param("companyId", com.epictalenttrust.hr.security.CurrentUser.companyId())
                .param("allJobs", true).param("people", java.util.List.of(new java.util.UUID(0, 0)))
                .query((rs, row) -> new ApplicationRow(uuid(rs, "id"), uuid(rs, "candidate_id"),
                        rs.getString("candidate_ref"), rs.getString("full_name"), rs.getString("city"),
                        rs.getString("country"), enumOf(rs, "cv_status", CvStatus.class),
                        enumOf(rs, "source", ApplicationSource.class), instant(rs, "created_at"),
                        integer(rs, "match_score"), enumOf(rs, "suggestion", AiSuggestion.class),
                        rs.getInt("must_hit"), rs.getInt("must_total"), integer(rs, "experience_years"),
                        rs.getString("certifications_found"), enumOf(rs, "decision", ShortlistDecisionType.class),
                        instant(rs, "decided_at"), enumOf(rs, "stage", ApplicationStage.class),
                        enumOf(rs, "status", ApplicationStatus.class), rs.getInt("must_backed"), rs.getString("summary"),
                        rs.getString("reason"), rs.getInt("red"), rs.getInt("red_open"), rs.getInt("checks"),
                        rs.getString("source_detail"), integer(rs, "criteria_version"), rs.getString("next_action"),
                        com.epictalenttrust.hr.candidate.LocationSummary.from(rs)))
                .list();
    }

    public Optional<ScreeningView> latestScreening(UUID applicationId) {
        return jdbc.sql("""
                        select id, model_version, created_at, match_score, must_have_pts, nice_to_have_pts, experience_pts,
                               certification_pts, experience_years, certifications_found, suggestion, criteria_version,
                               total_years, relevant_years, summary, reason, reading_quality, shortlist_cutoff, reserve_cutoff
                          from ai_screenings where application_id = :applicationId
                         order by created_at desc limit 1
                        """)
                .param("applicationId", applicationId)
                .query((rs, row) -> {
                    UUID id = uuid(rs, "id");
                    return new ScreeningView(id, rs.getString("model_version"), instant(rs, "created_at"),
                            rs.getInt("match_score"), rs.getInt("must_have_pts"), rs.getInt("nice_to_have_pts"),
                            rs.getInt("experience_pts"), rs.getInt("certification_pts"), integer(rs, "experience_years"),
                            rs.getString("certifications_found"), enumOf(rs, "suggestion", AiSuggestion.class),
                            evidence(id), integer(rs, "criteria_version"), decimal(rs, "total_years"),
                            decimal(rs, "relevant_years"), rs.getString("summary"), rs.getString("reason"),
                            rs.getString("reading_quality"), integer(rs, "shortlist_cutoff"),
                            integer(rs, "reserve_cutoff"), flags(id));
                })
                .optional();
    }

    public List<EvidenceView> evidence(UUID screeningId) {
        return jdbc.sql("""
                        select me.id, me.criterion_id, jc.kind, jc.value, me.matched, me.cv_quote, me.flag_note,
                               me.flagged_by, u.full_name as flagged_by_name, me.strength, me.points, me.cv_section,
                               me.matched_term, jc.min_years
                          from match_evidence me
                          join job_criteria jc on jc.id = me.criterion_id
                          left join hr_users u on u.id = me.flagged_by
                         where me.screening_id = :screeningId
                         order by jc.kind, me.points desc nulls last, me.matched desc, jc.value
                        """)
                .param("screeningId", screeningId)
                .query((rs, row) -> {
                    EvidenceStrength strength = enumOf(rs, "strength", EvidenceStrength.class);
                    return new EvidenceView(uuid(rs, "id"), uuid(rs, "criterion_id"),
                            enumOf(rs, "kind", CriterionKind.class), rs.getString("value"), rs.getBoolean("matched"),
                            rs.getString("cv_quote"), rs.getObject("flagged_by") != null, rs.getString("flag_note"),
                            rs.getString("flagged_by_name"), strength, strength == null ? null : strength.label(),
                            decimal(rs, "points"), rs.getString("cv_section"), rs.getString("matched_term"),
                            integer(rs, "min_years"));
                })
                .list();
    }

    public List<FlagView> flags(UUID screeningId) {
        return jdbc.sql("""
                        select f.id, f.severity, f.kind, f.message, f.cv_quote, f.review_outcome, f.review_note,
                               u.full_name as reviewer, f.reviewed_at
                          from screening_flags f left join hr_users u on u.id = f.reviewed_by
                         where f.screening_id = :id
                         order by case f.severity when 'red' then 0 else 1 end, f.created_at
                        """)
                .param("id", screeningId)
                .query((rs, row) -> new FlagView(uuid(rs, "id"), rs.getString("severity"), rs.getString("kind"),
                        rs.getString("message"), rs.getString("cv_quote"), rs.getString("review_outcome"),
                        rs.getString("review_note"), rs.getString("reviewer"), instant(rs, "reviewed_at")))
                .list();
    }

    public List<DecisionView> shortlistHistory(UUID applicationId) {
        return jdbc.sql("""
                        select d.decision, d.ai_suggestion, u.full_name, d.decided_at, d.reason
                          from shortlist_decisions d left join hr_users u on u.id = d.decided_by
                         where d.application_id = :applicationId
                         order by d.created_at desc
                        """)
                .param("applicationId", applicationId)
                .query((rs, row) -> new DecisionView(enumOf(rs, "decision", ShortlistDecisionType.class),
                        enumOf(rs, "ai_suggestion", AiSuggestion.class), rs.getString("full_name"),
                        instant(rs, "decided_at"), rs.getString("reason")))
                .list();
    }

    private static Double decimal(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.math.BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }

    /** The application that owns a piece of evidence, for tenancy checks. */
    public Optional<UUID> applicationForEvidence(UUID evidenceId) {
        return jdbc.sql("""
                        select s.application_id from match_evidence me join ai_screenings s on s.id = me.screening_id
                         where me.id = :id
                        """)
                .param("id", evidenceId)
                .query(UUID.class)
                .optional();
    }
}
