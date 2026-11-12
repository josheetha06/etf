package com.epictalenttrust.hr.sample;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.interview.AssessorLinkService;
import com.epictalenttrust.hr.interview.Interview;
import com.epictalenttrust.hr.interview.InterviewRepository;
import com.epictalenttrust.hr.interview.InterviewStatus;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.report.Recommendation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Fills in a sample report for an interview, so the whole flow can be tried before the AI interviewer, partner
 * assessors and panels are connected. The same candidate always gets the same sample, and every one is marked as a
 * sample in the database and on screen.
 */
@Service
public class SampleReportService {

    private final SampleReportProperties properties;
    private final Tenancy tenancy;
    private final InterviewRepository interviews;
    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final AssessorLinkService assessorLinks;
    private final com.epictalenttrust.hr.panel.PanelReviewService panels;
    private final NotificationService notifications;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final com.epictalenttrust.hr.candidate.CandidateLocationService locations;
    private final com.epictalenttrust.hr.application.ApplicationKeyFactsRepository keyFactsRepository;

    public SampleReportService(SampleReportProperties properties, Tenancy tenancy, InterviewRepository interviews,
                               JobApplicationRepository applications, JobRepository jobs, AssessorLinkService assessorLinks, com.epictalenttrust.hr.panel.PanelReviewService panels, NotificationService notifications,
                               JdbcClient jdbc, Clock clock,
                               com.epictalenttrust.hr.candidate.CandidateLocationService locations,
                               com.epictalenttrust.hr.application.ApplicationKeyFactsRepository keyFactsRepository) {
        this.locations = locations;
        this.keyFactsRepository = keyFactsRepository;
        this.properties = properties;
        this.tenancy = tenancy;
        this.interviews = interviews;
        this.applications = applications;
        this.jobs = jobs;
        this.assessorLinks = assessorLinks;
        this.panels = panels;
        this.notifications = notifications;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    /** HR clicked "Complete with sample report". */
    @Transactional
    public void completeNow(UUID interviewId) {
        if (!properties.enabled()) {
            throw ApiException.conflict("samples_off", "Sample reports are turned off for this installation.");
        }
        Interview interview = tenancy.interview(interviewId);
        complete(interview);
    }

    /** Used by the background job for interviews that have finished. */
    @Transactional
    public void completeFinished(UUID interviewId) {
        interviews.findById(interviewId).ifPresent(interview -> {
            if (interview.getStatus() == InterviewStatus.INVITED || interview.getStatus() == InterviewStatus.CONFIRMED) {
                complete(interview);
            }
        });
    }

    private void complete(Interview interview) {
        if (interview.getStatus() != InterviewStatus.INVITED && interview.getStatus() != InterviewStatus.CONFIRMED
                && interview.getStatus() != InterviewStatus.COMPLETED) {
            throw ApiException.conflict("not_upcoming", "Only a scheduled interview can be completed.");
        }
        // Tenancy was checked by the caller (HR) or isn't needed (background job).
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        Facts facts = facts(application);
        Random random = new Random(application.getId().getMostSignificantBits() ^ interview.getRound());
        Instant now = clock.instant();
        com.epictalenttrust.hr.job.RoundKind kind = jdbc.sql("select kind from job_rounds where job_id = :j and round = :r")
                .param("j", job.getId()).param("r", interview.getRound())
                .query((rs, n) -> Rows.enumOf(rs, "kind", com.epictalenttrust.hr.job.RoundKind.class)).optional()
                .orElseThrow(() -> ApiException.badRequest("unknown_round", "This job has no Round " + interview.getRound() + "."));
        String roundName = jdbc.sql("select name from job_rounds where job_id = :j and round = :r")
                .param("j", job.getId()).param("r", interview.getRound()).query(String.class).single();
        switch (kind) {
            case AI_INTERVIEW -> round1(interview, application, facts, random, now);
            case PARTNER -> round2(interview, facts, random);
            case PANEL -> panel(interview, random);
        }
        if (interview.getStatus() != InterviewStatus.COMPLETED) {
            interview.markCompleted(now);
        }
        jdbc.sql("insert into audit_log (actor_kind, action, record_type, record_id) values ('system', 'report.sample_created', 'interview', :id)")
                .param("id", interview.getId()).update();
        String label = roundName + " report ready (sample): ";
        notifications.notify(new NotificationService.Request(job.getCompanyId(),
                Arrays.asList(interview.getScheduledBy(), notifications.jobOwner(job.getId())), NotificationKind.REPORT_READY,
                label + facts.name(), "Review the report for " + job.getTitle() + " and decide the next step.",
                "/applications/" + application.getId(), job.getId(), application.getId(), interview.getId()));
    }

    // ------------------------------------------------------------------ Round 1: AI interview

    /** Places the AI interviewer might hear from a candidate whose CV gave no location: towns near Madurai. */
    private static final String[][] SAMPLE_PLACES = {
            {"Sivakasi", "626123"}, {"Virudhunagar", "626001"}, {"Melur", "625106"}, {"Karaikudi", "630001"},
            {"Theni", "625531"}, {"Rajapalayam", "626117"}};

    private void round1(Interview interview, JobApplication application, Facts facts, Random random, Instant now) {
        boolean exists = jdbc.sql("select exists (select 1 from round1_reports where interview_id = :i)")
                .param("i", interview.getId()).query(Boolean.class).single();
        if (exists) {
            throw ApiException.conflict("report_exists", "This interview already has a report.");
        }
        int ai = score(facts.matchScore(), random);
        int adjusted = Math.max(0, Math.min(100, ai + random.nextInt(7) - 3));
        Recommendation recommendation = recommend(adjusted);
        boolean idMismatch = random.nextInt(12) == 0;
        boolean roomConcern = random.nextInt(9) == 0;
        String identity = idMismatch ? "mismatch" : "verified";
        String identityNote = idMismatch
                ? "The name on the PAN card is spelt differently from the CV (initials only). Worth confirming at Round 2."
                : "Aadhaar and PAN shown on camera; the name and photo match the candidate.";
        String room = roomConcern ? "concern" : "clear";
        String roomNote = roomConcern
                ? "Another voice could be heard twice during the practical task. The candidate said a family member was nearby."
                : "Quiet room, candidate alone, no second screen seen during the camera sweep.";
        // The AI interviewer confirms where the candidate is, and asks when the CV didn't say.
        String[] spoken = jdbc.sql("select location_place, location_pin from candidates where id = :c and location_lat is not null")
                .param("c", application.getCandidateId())
                .query((rs, n) -> new String[]{rs.getString("location_place"), rs.getString("location_pin")})
                .optional()
                .orElseGet(() -> SAMPLE_PLACES[Math.floorMod(facts.name().hashCode(), SAMPLE_PLACES.length)]);
        // It also asks for the notice period and expected CTC when the CV left them out.
        record Missing(boolean notice, boolean ctc) {
        }
        Missing missing = jdbc.sql("""
                        select k.notice_period_days is null as notice, k.expected_ctc_lpa is null as ctc
                          from (select 1) x left join application_key_facts k on k.application_id = :a
                        """)
                .param("a", application.getId())
                .query((rs, n) -> new Missing(rs.getObject("notice") == null || rs.getBoolean("notice"),
                        rs.getObject("ctc") == null || rs.getBoolean("ctc")))
                .single();
        Integer saidNotice = missing.notice() ? new int[]{15, 30, 30, 45, 60, 60, 90}[random.nextInt(7)] : null;
        Double saidCtc = missing.ctc() ? Math.round((6 + random.nextInt(19) + random.nextInt(10) / 10.0) * 10) / 10.0 : null;
        List<String> skills = facts.skills();
        List<String> highlights = new ArrayList<>();
        highlights.add("Explained " + skills.get(0) + " with examples from their own work.");
        highlights.add(adjusted >= 70 ? "Worked through the practical task step by step and checked their answer."
                : "Needed hints to finish the practical task.");
        highlights.add(adjusted >= 60 ? "Clear and structured answers." : "Answers were short; follow-up questions helped.");
        String summary = adjusted >= 70
                ? "Solved the practical task and explained the trade-offs clearly."
                : adjusted >= 55 ? "Partly solved the practical task; the approach was sound but incomplete."
                : "Did not complete the practical task.";
        UUID reportId = jdbc.sql("""
                        insert into round1_reports (interview_id, ai_overall, final_overall, recommendation, task_summary,
                                                    integrity_status, reviewer_notes, status, released_at, identity_check,
                                                    identity_note, surroundings_check, surroundings_note, highlights,
                                                    is_sample, candidate_location, candidate_pin,
                                                    candidate_notice_days, candidate_expected_ctc_lpa)
                        values (:i, :ai, :final, cast(:rec as recommendation_t), :summary,
                                cast(:integrity as integrity_status_t), :notes, 'released', :at, :identity, :identityNote,
                                :room, :roomNote, :highlights, true, :location, :pin, :notice, :ctc)
                        returning id
                        """)
                .param("i", interview.getId()).param("ai", ai).param("final", adjusted)
                .param("rec", recommendation.name().toLowerCase(Locale.ROOT)).param("summary", summary)
                .param("integrity", idMismatch || roomConcern ? "flagged" : "clear")
                .param("notes", "Sample report. An ETT reviewer checked the AI interviewer's scores against the recording.")
                .param("at", Timestamp.from(now)).param("identity", identity).param("identityNote", identityNote)
                .param("room", room).param("roomNote", roomNote).param("highlights", highlights.toArray(String[]::new))
                .param("location", spoken[0]).param("pin", spoken[1]).param("notice", saidNotice).param("ctc", saidCtc)
                .query(UUID.class).single();
        locations.fromAiInterview(application.getCandidateId(), spoken[0], spoken[1]);
        if (saidNotice != null || saidCtc != null) {
            var keyFacts = keyFactsRepository.findByApplicationId(application.getId())
                    .orElseGet(() -> new com.epictalenttrust.hr.application.ApplicationKeyFacts(application.getId()));
            keyFacts.fillFromAiInterview(saidNotice, saidCtc);
            keyFactsRepository.save(keyFacts);
        }
        List<String> areas = new ArrayList<>(skills.subList(0, Math.min(3, skills.size())));
        areas.add("Problem solving");
        areas.add("Communication");
        for (String area : areas) {
            int draft = Math.max(1, Math.min(5, (int) Math.round(adjusted / 20.0) + random.nextInt(3) - 1));
            int fin = Math.max(1, Math.min(5, draft + (random.nextInt(5) == 0 ? (random.nextBoolean() ? 1 : -1) : 0)));
            jdbc.sql("""
                    insert into competency_scores (report_id, competency, ai_draft_score, final_score, reviewer_comment)
                    values (:r, :c, :d, :f, :comment)
                    """)
                    .param("r", reportId).param("c", area).param("d", draft).param("f", fin)
                    .param("comment", draft != fin ? "Adjusted after reviewing the recording" : null).update();
        }
    }

    // ------------------------------------------------------------------ Round 2: partner assessor

    private void round2(Interview interview, Facts facts, Random random) {
        Map<String, Integer> areas = new LinkedHashMap<>();
        int base = score(facts.matchScore(), random);
        for (String area : AssessorLinkService.AREAS) {
            areas.put(area, Math.max(1, Math.min(5, (int) Math.round(base / 20.0) + random.nextInt(3) - 1)));
        }
        int overall = (int) Math.round(areas.values().stream().mapToInt(Integer::intValue).average().orElse(0) / 5.0 * 100);
        boolean idOk = random.nextInt(15) != 0;
        String notes = overall >= 70
                ? "Completed the hands-on task at the centre without help. Good questions about the team and the role."
                : overall >= 55 ? "Finished most of the hands-on task. Some gaps in " + facts.skills().get(0).toLowerCase(Locale.ROOT) + "."
                : "Struggled with the hands-on task and ran out of time.";
        assessorLinks.saveAssessment(interview.getId(), interview.getAssessorId(), overall, recommend(overall), idOk,
                idOk ? "Original Aadhaar and PAN checked at the desk; both match the candidate."
                        : "The candidate brought a photocopy of the PAN card, not the original. Aadhaar matched.",
                toJson(areas), "Sample report. " + notes, true);
        jdbc.sql("update assessor_links set used_at = coalesce(used_at, now()) where interview_id = :i")
                .param("i", interview.getId()).update();
    }

    // ------------------------------------------------------------------ Panel interview

    private void panel(Interview interview, Random random) {
        boolean anyMembers = jdbc.sql("select exists (select 1 from panel_members where interview_id = :i)")
                .param("i", interview.getId()).query(Boolean.class).single();
        if (!anyMembers) {
            throw ApiException.conflict("no_panel", "This interview has no panel members.");
        }
        panels.saveSamples(interview.getId(), random);
    }

    // ------------------------------------------------------------------ helpers

    private record Facts(String name, Integer matchScore, List<String> skills) {
    }

    private Facts facts(JobApplication application) {
        String name = jdbc.sql("select full_name from candidates where id = :c").param("c", application.getCandidateId())
                .query(String.class).single();
        Integer score = jdbc.sql("select match_score from ai_screenings where application_id = :a order by created_at desc limit 1")
                .param("a", application.getId()).query(Integer.class).optional().orElse(null);
        List<String> skills = new ArrayList<>(jdbc.sql("""
                        select value from job_criteria where job_id = :j and retired_at is null and kind = 'must_have'
                         order by created_at
                        """)
                .param("j", application.getJobId()).query(String.class).list());
        if (skills.isEmpty()) {
            skills.add("Core skills");
        }
        return new Facts(name, score, skills);
    }

    /** Roughly follows the CV match, with some spread so not every candidate looks the same. */
    private static int score(Integer matchScore, Random random) {
        int base = matchScore == null ? 65 : 45 + (int) Math.round(matchScore * 0.45);
        return Math.max(30, Math.min(96, base + random.nextInt(17) - 8));
    }

    private static Recommendation recommend(int score) {
        return score >= 70 ? Recommendation.ADVANCE : score >= 55 ? Recommendation.HOLD : Recommendation.REJECT;
    }

    private static String toJson(Map<String, Integer> areas) {
        StringBuilder json = new StringBuilder("{");
        areas.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"')
                .append(k.replace("\\", "\\\\").replace("\"", "\\\"")).append("\":").append(v));
        return json.append('}').toString();
    }

    /** Interviews that ended a while ago and still have no report. */
    @Transactional(readOnly = true)
    public List<UUID> finishedWithoutReport() {
        if (!properties.enabled() || properties.autoCompleteMinutes() <= 0) {
            return List.of();
        }
        Instant cutoff = clock.instant().minusSeconds(properties.autoCompleteMinutes() * 60L);
        return jdbc.sql("""
                        select i.id from interviews i join applications a on a.id = i.application_id
                         where i.status in ('invited', 'confirmed') and i.start_at is not null and a.status = 'active'
                           and i.start_at + make_interval(mins => coalesce(i.duration_min, 45)) < :cutoff
                           and (not exists (select 1 from job_rounds jr where jr.job_id = a.job_id and jr.round = i.round
                                              and jr.kind = 'panel')
                                or exists (select 1 from panel_members p where p.interview_id = i.id))
                         order by i.start_at limit 50
                        """)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, row) -> Rows.uuid(rs, "id")).list();
    }
}
