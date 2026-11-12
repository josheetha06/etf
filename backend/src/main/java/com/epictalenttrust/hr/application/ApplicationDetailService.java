package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.application.ApplicationDtos.DecisionView;
import com.epictalenttrust.hr.application.ApplicationDtos.ScreeningView;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.ChangeLog;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.interview.InterviewQueries;
import com.epictalenttrust.hr.interview.InterviewStatus;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobDtos.Changes;
import com.epictalenttrust.hr.job.JobRound;
import com.epictalenttrust.hr.job.JobRoundRepository;
import com.epictalenttrust.hr.job.JobStatus;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.message.MessageQueries;
import com.epictalenttrust.hr.message.MessageQueries.MessageView;
import com.epictalenttrust.hr.panel.PanelDtos.PanelReport;
import com.epictalenttrust.hr.panel.PanelReviewService;
import com.epictalenttrust.hr.report.ReportDtos.ReportView;
import com.epictalenttrust.hr.report.ReportDtos.Round2View;
import com.epictalenttrust.hr.report.ReportDtos.StageDecisionView;
import com.epictalenttrust.hr.report.ReportQueries;
import com.epictalenttrust.hr.security.Access;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Everything HR sees on a candidate's page for one job. */
@Service
public class ApplicationDetailService {

    public record CandidateView(UUID id, String candidateRef, String fullName, String email, String mobile, String city,
                                String country) {
    }

    public record JobRef(UUID id, String jobRef, String title, JobStatus status, boolean aiShortlisting,
                         String statusReason, int roundsTotal) {
    }

    public record ApplicationDetail(UUID id, CandidateView candidate, JobRef job, ApplicationStage stage,
                                    ApplicationStatus status, CvStatus cvStatus, boolean cvAvailable,
                                    LocalDate cvDeleteAfter, Instant addedAt, String addedByName,
                                    ScreeningView screening, List<DecisionView> shortlistHistory,
                                    List<InterviewRow> interviews, List<RoundDetail> rounds,
                                    List<StageDecisionView> stageDecisions, List<MessageView> messages,
                                    ApplicationSource source, String sourceDetail, String statusReason,
                                    NextStep nextStep, List<ReviewView> reviews, List<OtherJob> otherJobs,
                                    Changes changes, String coveringFor, CandidateLocation location) {
    }

    /**
     * Where the candidate is and their nearest partner assessment centre city.
     *
     * @param label  the place on the map in full ("Sivakasi, Virudhunagar, Tamil Nadu 626123")
     * @param status known, unplaced, ask_ai or missing (see LocationSummary)
     */
    public record CandidateLocation(String text, String label, String source, String status, Instant updatedAt,
                                    String nearestCity, Integer nearestCityMinutes, Double nearestCityKm,
                                    int nearestCityCentres) {
    }

    /**
     * One round of the job's plan for this candidate: where they are with it, the interview, its report (by the
     * round's type) and HR's decision.
     */
    public record RoundDetail(int round, String name, RoundKind kind, InterviewMode mode, Integer durationMin,
                              Integer targetDays, String state, Instant dueAt, boolean overdue, InterviewRow interview,
                              ReportView aiReport, Round2View partnerReport, PanelReport panelReport,
                              StageDecisionView decision) {
    }

    /** What HR should do next for this candidate (same logic as every list in the portal). */
    public record NextStep(String action, Integer round, String roundName, RoundKind roundKind, UUID pendingRequestId,
                           int redFlagsOpen, Instant dueAt, boolean overdue) {
    }

    /** HR's review of an AI report: agreed, overrode (with reason) or asked ETT to re-check. */
    public record ReviewView(String reportType, String outcome, String note, String reviewedByName, Instant reviewedAt) {
    }

    /** The same candidate in this company's other jobs. */
    public record OtherJob(UUID applicationId, UUID jobId, String jobTitle, String jobRef, ApplicationStage stage,
                           ApplicationStatus status) {
    }

    private final Tenancy tenancy;
    private final Access access;
    private final CandidateRepository candidates;
    private final HrUserRepository users;
    private final JobRoundRepository rounds;
    private final ApplicationQueries applicationQueries;
    private final InterviewQueries interviewQueries;
    private final ReportQueries reportQueries;
    private final MessageQueries messageQueries;
    private final PanelReviewService panels;
    private final ChangeLog changeLog;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final com.epictalenttrust.hr.candidate.CandidateLocationService locations;
    private final com.epictalenttrust.hr.routing.CentreOptionsService centres;

    public ApplicationDetailService(Tenancy tenancy, Access access, CandidateRepository candidates, HrUserRepository users,
                                    JobRoundRepository rounds, ApplicationQueries applicationQueries,
                                    InterviewQueries interviewQueries, ReportQueries reportQueries,
                                    MessageQueries messageQueries, PanelReviewService panels, ChangeLog changeLog,
                                    JdbcClient jdbc, Clock clock,
                                    com.epictalenttrust.hr.candidate.CandidateLocationService locations,
                                    com.epictalenttrust.hr.routing.CentreOptionsService centres) {
        this.locations = locations;
        this.centres = centres;
        this.tenancy = tenancy;
        this.access = access;
        this.candidates = candidates;
        this.users = users;
        this.rounds = rounds;
        this.applicationQueries = applicationQueries;
        this.interviewQueries = interviewQueries;
        this.reportQueries = reportQueries;
        this.messageQueries = messageQueries;
        this.panels = panels;
        this.changeLog = changeLog;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ApplicationDetail detail(UUID applicationId) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        String addedBy = application.getCvUploadedBy() == null ? null
                : users.findById(application.getCvUploadedBy()).map(u -> u.getFullName()).orElse(null);
        List<JobRound> plan = rounds.findByJobIdOrderByRound(job.getId());
        List<InterviewRow> interviews = interviewQueries.forApplication(applicationId);
        List<StageDecisionView> decisions = reportQueries.decisions(applicationId);
        UUID covering = access.coveringFor(job.getOwnerId(), job.getHiringManagerId());
        return new ApplicationDetail(application.getId(),
                new CandidateView(candidate.getId(), candidate.getCandidateRef(), candidate.getFullName(),
                        candidate.getEmail(), candidate.getMobile(), candidate.getCity(), candidate.getCountry()),
                new JobRef(job.getId(), job.getJobRef(), job.getTitle(), job.getStatus(), job.isAiShortlisting(),
                        job.getStatusReason(), plan.size()),
                application.getStage(), application.getStatus(), application.getCvStatus(),
                application.getCvFileKey() != null, application.getCvDeleteAfter(), application.getCreatedAt(), addedBy,
                applicationQueries.latestScreening(applicationId).orElse(null),
                applicationQueries.shortlistHistory(applicationId),
                interviews, rounds(application, plan, interviews, decisions), decisions,
                messageQueries.forApplication(applicationId),
                application.getSource(), application.getSourceDetail(), application.getStatusReason(),
                nextStep(applicationId), reviews(applicationId), otherJobs(application),
                changes(application), covering == null ? null : users.findById(covering).map(u -> u.getFullName()).orElse(null),
                location(application));
    }

    @org.springframework.transaction.annotation.Transactional
    public CandidateLocation setLocation(UUID applicationId, String text) {
        JobApplication application = tenancy.application(applicationId);
        locations.setByHr(application.getCandidateId(), text);
        return location(application);
    }

    CandidateLocation location(JobApplication application) {
        var summary = access.bind(jdbc.sql(PipelineQueries.PIPELINE + """
                        select location_text, location_place, location_district, location_source, location_placed,
                               ai_round_ahead
                          from pipeline where application_id = :id
                        """))
                .param("id", application.getId())
                .query((rs, row) -> com.epictalenttrust.hr.candidate.LocationSummary.from(rs))
                .optional().orElse(new com.epictalenttrust.hr.candidate.LocationSummary(null, null, null, "missing"));
        var full = locations.find(application.getCandidateId());
        var nearest = centres.nearest(application.getCandidateId());
        return new CandidateLocation(summary.text(), full.map(l -> l.label()).orElse(null), summary.source(),
                summary.status(), full.map(l -> l.updatedAt()).orElse(null),
                nearest == null ? null : nearest.name(), nearest == null ? null : nearest.travelMinutes(),
                nearest == null ? null : nearest.roadKm(), nearest == null ? 0 : nearest.centres());
    }

    NextStep nextStep(UUID applicationId) {
        return access.bind(jdbc.sql(PipelineQueries.PIPELINE + """
                        select next_action, current_round, round_name, round_kind, pending_request_id, red_open, due_at
                          from pipeline where application_id = :id
                        """))
                .param("id", applicationId)
                .query((rs, row) -> {
                    Instant due = Rows.instant(rs, "due_at");
                    return new NextStep(rs.getString("next_action"), Rows.integer(rs, "current_round"),
                            rs.getString("round_name"), Rows.enumOf(rs, "round_kind", RoundKind.class),
                            Rows.uuid(rs, "pending_request_id"), rs.getInt("red_open"), due,
                            due != null && due.isBefore(clock.instant()));
                })
                .optional().orElse(new NextStep("none", null, null, null, null, 0, null, false));
    }

    /** Each round of the job's plan as it stands for this candidate. */
    private List<RoundDetail> rounds(JobApplication application, List<JobRound> plan, List<InterviewRow> interviews,
                                     List<StageDecisionView> decisions) {
        int current = application.getStage() == ApplicationStage.SHORTLIST ? 1 : application.getStage().roundNumber();
        boolean pastAll = application.getStage() == ApplicationStage.OFFER;
        boolean stopped = !application.isActive();
        Instant now = clock.instant();
        List<RoundDetail> out = new ArrayList<>();
        for (JobRound round : plan) {
            int n = round.getRound();
            InterviewRow interview = interviews.stream()
                    .filter(i -> i.round() == n && i.status() != InterviewStatus.CANCELLED)
                    .reduce((first, second) -> second).orElse(null);
            StageDecisionView decision = decisions.stream()
                    .filter(d -> d.stage() == ApplicationStage.forRound(n)).findFirst().orElse(null);
            String state;
            if (pastAll || (current > 0 && n < current)) {
                state = "passed";
            } else if (current == n) {
                state = stopped ? "stopped" : "current";
            } else {
                state = stopped || current == 0 ? "not_reached" : "upcoming";
            }
            Instant due = "current".equals(state) && round.getTargetDays() != null && application.getStageEnteredAt() != null
                    ? application.getStageEnteredAt().plus(java.time.Duration.ofDays(round.getTargetDays())) : null;
            ReportView ai = null;
            Round2View partner = null;
            PanelReport panel = null;
            if (interview != null) {
                switch (round.getKind()) {
                    case AI_INTERVIEW -> ai = reportQueries.aiReport(interview.id()).orElse(null);
                    case PARTNER -> partner = reportQueries.partnerReport(interview.id()).orElse(null);
                    case PANEL -> panel = panels.report(interview.id());
                }
            }
            out.add(new RoundDetail(n, round.getName(), round.getKind(), round.getMode(), round.getDurationMin(),
                    round.getTargetDays(), state, due, due != null && due.isBefore(now), interview, ai, partner, panel,
                    decision));
        }
        return out;
    }

    /** Created and last changed, counting changes to the candidate's details and to this application. */
    private Changes changes(JobApplication application) {
        Map<UUID, Changes> both = changeLog.forRecords(List.of("applications", "candidates"),
                List.of(application.getId(), application.getCandidateId()));
        Changes app = both.getOrDefault(application.getId(), new Changes(null, application.getCreatedAt(), null, null));
        Changes cand = both.get(application.getCandidateId());
        if (cand != null && cand.lastUpdatedAt() != null
                && (app.lastUpdatedAt() == null || cand.lastUpdatedAt().isAfter(app.lastUpdatedAt()))) {
            return new Changes(app.createdByName(), app.createdAt(), cand.lastUpdatedByName(), cand.lastUpdatedAt());
        }
        return app;
    }

    private List<ReviewView> reviews(UUID applicationId) {
        return jdbc.sql("""
                        select r.report_type, r.outcome, r.note, u.full_name, r.reviewed_at
                          from report_reviews r left join hr_users u on u.id = r.reviewed_by
                         where r.application_id = :id order by r.reviewed_at desc
                        """)
                .param("id", applicationId)
                .query((rs, row) -> new ReviewView(rs.getString("report_type"), rs.getString("outcome"),
                        rs.getString("note"), rs.getString("full_name"), Rows.instant(rs, "reviewed_at")))
                .list();
    }

    private List<OtherJob> otherJobs(JobApplication application) {
        return access.bind(jdbc.sql("""
                        select a.id, j.id as job_id, j.title, j.job_ref, a.stage, a.status
                          from applications a join jobs j on j.id = a.job_id
                         where a.candidate_id = :c and a.id <> :a and j.company_id = :companyId
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                         order by a.created_at desc
                        """))
                .param("c", application.getCandidateId()).param("a", application.getId())
                .query((rs, row) -> new OtherJob(Rows.uuid(rs, "id"), Rows.uuid(rs, "job_id"), rs.getString("title"),
                        rs.getString("job_ref"), Rows.enumOf(rs, "stage", ApplicationStage.class),
                        Rows.enumOf(rs, "status", ApplicationStatus.class)))
                .list();
    }
}
