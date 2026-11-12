package com.epictalenttrust.hr.application;

/**
 * The state of every application in one SQL view: where the candidate is, what's been decided, and what HR needs
 * to do next. Used by the candidate list, the Reports page, the Dashboard and the candidate's "Next step" panel, so
 * every screen agrees.
 *
 * <p>Each job has its own rounds (up to 8, any type, any order). A candidate in stage {@code roundN} is in the job's
 * nth round; {@code shortlist} means "shortlisted, Round 1 not booked yet". The round's report depends on its type:
 * an AI interview report (released by ETT), a partner assessor's report, or the panel's reviews.
 *
 * <p>Next actions, in priority order: answer_reschedule, review_flags, decide_shortlist, schedule_round, decide_round,
 * record_offer; otherwise waiting (something is in progress) or none (nothing more to do).
 *
 * <p>Callers bind {@code companyId}, {@code allJobs} and {@code people} (see {@code Access#bind}): recruiters and
 * hiring managers only see their own jobs and the jobs of anyone they are covering.
 */
public final class PipelineQueries {

    private PipelineQueries() {
    }

    /** A CTE named {@code pipeline}; callers add {@code select … from pipeline where …}. */
    public static final String PIPELINE = """
            with pipeline as (
            select a.id as application_id, a.job_id, j.title as job_title, j.job_ref, j.company_id, j.status as job_status,
                   j.owner_id as job_owner_id, j.hiring_manager_id, j.ai_shortlisting,
                   a.stage, a.status, a.source, a.source_detail, a.created_at as added_at, a.stage_entered_at,
                   c.id as candidate_id, c.candidate_ref, c.full_name, c.email, c.mobile, c.city, c.country,
                   c.location_text, c.location_place, c.location_district, c.location_region, c.location_pin,
                   c.location_source, c.location_lat is not null as location_placed,
                   s.id as screening_id, s.match_score, s.suggestion, s.summary, s.reason, s.created_at as screened_at,
                   d.decision as shortlist_decision, d.decided_at as shortlist_decided_at,
                   coalesce(fl.red_open, 0) as red_open, coalesce(fl.total, 0) as flags_total,
                   coalesce(fl.red_total, 0) as red_total,
                   cr.n as current_round, jr.name as round_name, jr.kind as round_kind, jr.mode as round_mode,
                   jr.duration_min as round_duration, jr.target_days,
                   (select count(*) from job_rounds x where x.job_id = j.id) as rounds_total,
                   exists (select 1 from job_rounds x where x.job_id = j.id and x.kind = 'ai_interview'
                             and x.round >= coalesce(cr.n, 1)
                             and not exists (select 1 from interviews xi join round1_reports xr on xr.interview_id = xi.id
                                              where xi.application_id = a.id and xi.round = x.round)) as ai_round_ahead,
                   i.id as round_interview_id, i.status as round_interview_status, i.start_at as round_start,
                   case jr.kind
                     when 'ai_interview' then ar.released_at
                     when 'partner' then pa.submitted_at
                     when 'panel' then pr.last_submitted
                   end as report_ready_at,
                   case jr.kind
                     when 'ai_interview' then ar.status = 'released'
                     when 'partner' then pa.submitted_at is not null
                     when 'panel' then coalesce(pr.submitted, 0) > 0
                          and (coalesce(pr.submitted, 0) >= coalesce(pm.members, 0)
                               or i.start_at + make_interval(mins => coalesce(i.duration_min, 60)) < now())
                     else false
                   end as report_ready,
                   case jr.kind
                     when 'ai_interview' then ar.final_overall
                     when 'partner' then pa.score
                     when 'panel' then round(pr.avg_score * 20)::int
                   end as report_score,
                   case jr.kind
                     when 'ai_interview' then ar.recommendation::text
                     when 'partner' then pa.recommendation::text
                     when 'panel' then pr.recommendation
                   end as report_recommendation,
                   case jr.kind
                     when 'ai_interview' then coalesce(ar.is_sample, false)
                     when 'partner' then coalesce(pa.is_sample, false)
                     when 'panel' then coalesce(pr.sample, false)
                     else false
                   end as report_sample,
                   coalesce(pr.submitted, 0) as panel_submitted, coalesce(pm.members, 0) as panel_members,
                   ar.status as ai_report_status, ar.recheck_requested_at,
                   sd.decision as round_decision, sd.created_at as round_decided_at,
                   case when cr.n is not null and jr.target_days is not null and a.stage_entered_at is not null
                        then a.stage_entered_at + make_interval(days => jr.target_days) end as due_at,
                   rr.id as pending_request_id,
                   case
                     when a.status <> 'active' then 'none'
                     when rr.id is not null then 'answer_reschedule'
                     when a.stage = 'screening' and s.id is not null and d.decision is null and coalesce(fl.red_open, 0) > 0
                          then 'review_flags'
                     when a.stage = 'screening' and s.id is not null and d.decision is null then 'decide_shortlist'
                     when cr.n is not null and jr.id is not null and (i.id is null or i.status = 'draft') then 'schedule_round'
                     when cr.n is not null and (sd.decision is null or sd.decision = 'hold')
                          and (case jr.kind
                                 when 'ai_interview' then ar.status = 'released'
                                 when 'partner' then pa.submitted_at is not null
                                 when 'panel' then coalesce(pr.submitted, 0) > 0
                                      and (coalesce(pr.submitted, 0) >= coalesce(pm.members, 0)
                                           or i.start_at + make_interval(mins => coalesce(i.duration_min, 60)) < now())
                                 else false end)
                          then 'decide_round'
                     when a.stage = 'offer' then 'record_offer'
                     when a.stage = 'screening' and s.id is null then 'waiting'
                     when a.stage = 'screening' then 'none'
                     else 'waiting'
                   end as next_action
              from applications a
              join jobs j on j.id = a.job_id
              join candidates c on c.id = a.candidate_id
              cross join lateral (select case when a.stage = 'shortlist' then 1
                                              when a.stage::text like 'round_' then substr(a.stage::text, 6)::int
                                         end as n) cr
              left join job_rounds jr on jr.job_id = j.id and jr.round = cr.n
              left join lateral (select id, match_score, suggestion, summary, reason, created_at from ai_screenings
                                  where application_id = a.id order by created_at desc limit 1) s on true
              left join lateral (select decision, decided_at from shortlist_decisions where application_id = a.id
                                  order by created_at desc limit 1) d on true
              left join lateral (select count(*) filter (where severity = 'red' and review_outcome is null) as red_open,
                                        count(*) filter (where severity = 'red') as red_total,
                                        count(*) as total
                                   from screening_flags where screening_id = s.id) fl on true
              left join lateral (select id, status, start_at, duration_min from interviews
                                  where application_id = a.id and round = cr.n and status not in ('cancelled', 'no_show')
                                  order by created_at desc limit 1) i on true
              left join round1_reports ar on jr.kind = 'ai_interview' and ar.interview_id = i.id
              left join lateral (select submitted_at, score, recommendation, is_sample from round2_assessments
                                  where jr.kind = 'partner' and interview_id = i.id and submitted_at is not null
                                  order by submitted_at desc limit 1) pa on true
              left join lateral (select count(*) filter (where submitted_at is not null) as submitted,
                                        max(submitted_at) as last_submitted, bool_or(is_sample) as sample,
                                        avg(overall_score) filter (where submitted_at is not null) as avg_score,
                                        mode() within group (order by case when recommendation in ('strong_hire', 'hire')
                                                                           then 'advance' else 'reject' end)
                                            filter (where submitted_at is not null) as recommendation
                                   from panel_reviews where jr.kind = 'panel' and interview_id = i.id) pr on true
              left join lateral (select count(*) as members from panel_members
                                  where jr.kind = 'panel' and interview_id = i.id) pm on true
              left join lateral (select decision, created_at from stage_decisions
                                  where application_id = a.id and cr.n is not null and stage::text = 'round' || cr.n
                                  order by created_at desc limit 1) sd on true
              left join lateral (select r.id from reschedule_requests r join interviews ri on ri.id = r.interview_id
                                  where ri.application_id = a.id and r.status = 'pending' limit 1) rr on true
             where (cast(:companyId as uuid) is null or j.company_id = :companyId)
               and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
            )
            """;

    /** Actions that need an HR decision (as opposed to scheduling or waiting). */
    public static final String DECISION_ACTIONS =
            "('answer_reschedule', 'review_flags', 'decide_shortlist', 'decide_round', 'record_offer')";

    public static final String SCHEDULE_ACTIONS = "('schedule_round')";

    /** For queries about one job the caller has already checked the signed-in person may see. */
    public static org.springframework.jdbc.core.simple.JdbcClient.StatementSpec bindCheckedJob(
            org.springframework.jdbc.core.simple.JdbcClient.StatementSpec spec) {
        return spec.param("companyId", com.epictalenttrust.hr.security.CurrentUser.companyId()).param("allJobs", true)
                .param("people", java.util.List.of(new java.util.UUID(0, 0)));
    }

    /** For background jobs: every company and every job. Never for a signed-in person (see {@code Access#bind}). */
    public static org.springframework.jdbc.core.simple.JdbcClient.StatementSpec bindSystem(
            org.springframework.jdbc.core.simple.JdbcClient.StatementSpec spec) {
        return spec.param("companyId", null, java.sql.Types.OTHER).param("allJobs", true)
                .param("people", java.util.List.of(new java.util.UUID(0, 0)));
    }
}
