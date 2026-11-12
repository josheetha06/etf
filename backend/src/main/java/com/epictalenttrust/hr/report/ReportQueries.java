package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.report.ReportDtos.CompetencyView;
import com.epictalenttrust.hr.report.ReportDtos.ReportView;
import com.epictalenttrust.hr.report.ReportDtos.Round2View;
import com.epictalenttrust.hr.report.ReportDtos.StageDecisionView;
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

@Component
@Transactional(readOnly = true)
public class ReportQueries {

    private final JdbcClient jdbc;

    public ReportQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The AI interview report for one interview, once an ETT reviewer has released it (or HR asked for a re-check). */
    public Optional<ReportView> aiReport(UUID interviewId) {
        return jdbc.sql("""
                        select r.id, r.interview_id, i.start_at, s.full_name as reviewer, r.ai_overall, r.final_overall,
                               r.recommendation, r.task_summary, r.integrity_status, r.reviewer_notes, r.released_at,
                               r.identity_check, r.identity_note, r.surroundings_check, r.surroundings_note, r.highlights,
                               r.is_sample, r.recheck_requested_at, r.recheck_note, r.status, r.candidate_location,
                               r.candidate_pin, r.candidate_notice_days, r.candidate_expected_ctc_lpa
                          from round1_reports r
                          join interviews i on i.id = r.interview_id
                          left join ett_staff s on s.id = r.reviewer_id
                         where i.id = :interviewId and r.status in ('released', 'in_review')
                           and (r.status = 'released' or r.recheck_requested_at is not null)
                         order by r.released_at desc nulls last limit 1
                        """)
                .param("interviewId", interviewId)
                .query((rs, row) -> {
                    UUID id = uuid(rs, "id");
                    return new ReportView(id, uuid(rs, "interview_id"), instant(rs, "start_at"), rs.getString("reviewer"),
                            integer(rs, "ai_overall"), integer(rs, "final_overall"),
                            enumOf(rs, "recommendation", Recommendation.class), rs.getString("task_summary"),
                            enumOf(rs, "integrity_status", IntegrityStatus.class), rs.getString("reviewer_notes"),
                            instant(rs, "released_at"), competencies(id), rs.getString("identity_check"),
                            rs.getString("identity_note"), rs.getString("surroundings_check"),
                            rs.getString("surroundings_note"), textArray(rs.getArray("highlights")),
                            rs.getBoolean("is_sample"), instant(rs, "recheck_requested_at"), rs.getString("recheck_note"),
                            rs.getString("status"), rs.getString("candidate_location"), rs.getString("candidate_pin"),
                            integer(rs, "candidate_notice_days"),
                            rs.getObject("candidate_expected_ctc_lpa") == null ? null : rs.getDouble("candidate_expected_ctc_lpa"));
                })
                .optional();
    }

    public List<CompetencyView> competencies(UUID reportId) {
        return jdbc.sql("""
                        select competency, ai_draft_score, final_score, reviewer_comment
                          from competency_scores where report_id = :id order by created_at, competency
                        """)
                .param("id", reportId)
                .query((rs, row) -> new CompetencyView(rs.getString("competency"), integer(rs, "ai_draft_score"),
                        integer(rs, "final_score"), rs.getString("reviewer_comment")))
                .list();
    }

    public List<StageDecisionView> decisions(UUID applicationId) {
        return jdbc.sql("""
                        select d.id, d.stage, d.decision, d.reason, u.full_name, d.decided_at, d.challenged_at,
                               case when d.decided_at > now() - interval '5 minutes'
                                         and (m.id is null or (m.sent_at is null and m.cancelled_at is null))
                                         and d.id = (select id from stage_decisions d2 where d2.application_id = d.application_id
                                                      order by d2.created_at desc limit 1)
                                    then d.decided_at + interval '5 minutes' end as undo_until
                          from stage_decisions d left join hr_users u on u.id = d.decided_by
                          left join messages m on m.id = d.message_id
                         where d.application_id = :id order by d.created_at desc
                        """)
                .param("id", applicationId)
                .query((rs, row) -> new StageDecisionView(uuid(rs, "id"), enumOf(rs, "stage", ApplicationStage.class),
                        enumOf(rs, "decision", StageDecisionType.class), rs.getString("reason"), rs.getString("full_name"),
                        instant(rs, "decided_at"), instant(rs, "challenged_at"), instant(rs, "undo_until")))
                .list();
    }

    /** The partner assessor's report for one interview, once sent. */
    public Optional<Round2View> partnerReport(UUID interviewId) {
        return jdbc.sql("""
                        select i.id, i.start_at, pc.name as centre, pa.full_name as assessor, r.score, r.recommendation,
                               r.id_checked, r.id_check_note, r.area_scores::text as areas, r.notes, r.submitted_at, r.is_sample
                          from interviews i
                          join round2_assessments r on r.interview_id = i.id
                          left join centre_slots cs on cs.id = i.slot_id
                          left join partner_centres pc on pc.id = cs.centre_id
                          left join partner_assessors pa on pa.id = coalesce(r.assessor_id, i.assessor_id)
                         where i.id = :id and r.submitted_at is not null
                         order by r.submitted_at desc limit 1
                        """)
                .param("id", interviewId)
                .query((rs, row) -> new Round2View(uuid(rs, "id"), instant(rs, "start_at"), rs.getString("centre"),
                        rs.getString("assessor"), integer(rs, "score"), enumOf(rs, "recommendation", Recommendation.class),
                        (Boolean) rs.getObject("id_checked"), rs.getString("id_check_note"), areas(rs.getString("areas")),
                        rs.getString("notes"), instant(rs, "submitted_at"), rs.getBoolean("is_sample")))
                .optional();
    }

    static java.util.Map<String, Integer> areas(String json) {
        java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)").matcher(json);
        while (m.find()) {
            out.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        return out;
    }

    static List<String> textArray(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return java.util.Arrays.stream(values).map(String::valueOf).toList();
    }
}
