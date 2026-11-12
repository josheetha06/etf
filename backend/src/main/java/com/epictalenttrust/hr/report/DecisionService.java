package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.interview.InterviewFacts;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.report.ReportDtos.DecisionInput;
import com.epictalenttrust.hr.report.ReportDtos.StageDecisionView;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * HR's decision after each round's report: Progress, Hold or Not progressing. Going against the report's
 * recommendation needs a short reason; "Not progressing" needs a reason the candidate will see and can challenge.
 * Progress opens the job's next round, and after the last round it's an offer.
 */
@Service
public class DecisionService {

    /** How long HR has to undo a decision; the candidate's email waits this long. */
    public static final java.time.Duration UNDO_WINDOW = java.time.Duration.ofMinutes(5);

    private final Tenancy tenancy;
    private final StageDecisionRepository decisions;
    private final ReportQueries reports;
    private final CandidateRepository candidates;
    private final CompanyRepository companies;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final ActivityService activity;
    private final com.epictalenttrust.hr.job.JobRoundRepository rounds;
    private final com.epictalenttrust.hr.panel.PanelReviewService panels;
    private final JdbcClient jdbc;
    private final Clock clock;

    public DecisionService(Tenancy tenancy, StageDecisionRepository decisions, ReportQueries reports,
                           CandidateRepository candidates, CompanyRepository companies, MessageService messages,
                           MessageTemplates templates, ActivityService activity,
                           com.epictalenttrust.hr.job.JobRoundRepository rounds,
                           com.epictalenttrust.hr.panel.PanelReviewService panels, JdbcClient jdbc, Clock clock) {
        this.tenancy = tenancy;
        this.decisions = decisions;
        this.reports = reports;
        this.candidates = candidates;
        this.companies = companies;
        this.messages = messages;
        this.templates = templates;
        this.activity = activity;
        this.rounds = rounds;
        this.panels = panels;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public List<StageDecisionView> decide(UUID applicationId, DecisionInput input) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        if (job.getStatus() == com.epictalenttrust.hr.job.JobStatus.CLOSED) {
            throw ApiException.conflict("job_closed", "This job is closed. Open it again to make decisions.");
        }
        ApplicationStage stage = input.stage();
        if (!stage.isRound()) {
            throw ApiException.badRequest("stage_not_available", "Decisions are made after an interview round.");
        }
        int round = stage.roundNumber();
        var plan = rounds.findByJobIdAndRound(job.getId(), round).orElse(null);
        String label = MessageTemplates.label(plan == null ? null : plan.getName(), round);
        if (!application.isActive() || application.getStage() != stage) {
            throw ApiException.conflict("wrong_stage", "This candidate isn't waiting for a " + label + " decision.");
        }
        // "Not progressing" is allowed without a report (for example after a no-show); everything else needs one.
        Recommendation recommendation = recommendation(applicationId, round, plan == null ? null : plan.getKind(),
                input.decision() != StageDecisionType.REJECT);
        boolean finalDecisionMade = decisions.findByApplicationIdAndStageOrderByCreatedAtDesc(applicationId, stage)
                .stream().anyMatch(d -> d.getDecision() != StageDecisionType.HOLD);
        if (finalDecisionMade) {
            throw ApiException.conflict("already_decided", "A " + label + " decision has already been made.");
        }
        String reason = input.reason() == null ? null : input.reason().strip();
        if (input.decision() == StageDecisionType.REJECT && (reason == null || reason.length() < 10)) {
            throw ApiException.badRequest("reason_required",
                    "Please give a reason (at least 10 characters). The candidate will see it and can challenge it.");
        }
        boolean overrides = recommendation != null && !recommendation.name().equals(input.decision().name());
        if (overrides && (reason == null || reason.length() < 5)) {
            throw ApiException.badRequest("reason_required",
                    "You're going against the report's recommendation, so add a short reason (at least 5 characters).");
        }
        StageDecision saved = new StageDecision(applicationId, stage, input.decision(), reason, CurrentUser.id(),
                clock.instant());
        saved.rememberBefore(application.getStage(), application.getStageEnteredAt());
        decisions.save(saved);
        if (recommendation != null) {
            recordReview(applicationId, stage, overrides ? "override" : "agree", reason);
        }

        String firstName = candidates.findById(application.getCandidateId())
                .map(c -> InterviewFacts.firstName(c.getFullName())).orElse("there");
        String company = companies.findById(job.getCompanyId()).map(c -> c.getName()).orElse("the hiring company");
        // The candidate's email waits a few minutes, so the decision can still be undone.
        switch (input.decision()) {
            case ADVANCE -> {
                int total = rounds.findByJobIdOrderByRound(job.getId()).size();
                if (round >= total) {
                    application.moveTo(ApplicationStage.OFFER);
                    saved.attachMessage(messages.emailCandidateLater(application, null, SenderKind.HR_USER,
                            CurrentUser.id(), templates.offer(job.getCompanyId(), firstName, job.getTitle(), company),
                            UNDO_WINDOW).getId());
                } else {
                    int next = round + 1;
                    String nextName = rounds.findByJobIdAndRound(job.getId(), next).map(r -> r.getName()).orElse(null);
                    application.moveTo(ApplicationStage.forRound(next));
                    saved.attachMessage(messages.emailCandidateLater(application, null, SenderKind.HR_USER,
                            CurrentUser.id(), templates.progressed(job.getCompanyId(), firstName, job.getTitle(), company,
                                    next, nextName), UNDO_WINDOW).getId());
                }
            }
            case REJECT -> {
                application.reject(reason, clock.instant());
                saved.attachMessage(messages.emailCandidateLater(application, null, SenderKind.HR_USER, CurrentUser.id(),
                        templates.notProgressing(job.getCompanyId(), firstName, job.getTitle(), company, reason),
                        UNDO_WINDOW).getId());
            }
            case HOLD -> {
                // Stays in this round; HR decides later.
            }
        }
        activity.record("stage.decided", "application", applicationId);
        return reports.decisions(applicationId);
    }

    /**
     * The round's report recommendation, by the round's type: the AI interview report (once released by ETT), the
     * partner assessor's report, or the panel's reviews (most reviewers' view). With {@code required}, a missing
     * report is an error; otherwise null.
     */
    private Recommendation recommendation(UUID applicationId, int round, com.epictalenttrust.hr.job.RoundKind kind,
                                          boolean required) {
        UUID interviewId = jdbc.sql("""
                        select id from interviews where application_id = :a and round = :r
                           and status not in ('cancelled', 'no_show') order by created_at desc limit 1
                        """)
                .param("a", applicationId).param("r", round).query(UUID.class).optional().orElse(null);
        Recommendation found = null;
        if (interviewId != null && kind != null) {
            found = switch (kind) {
                case AI_INTERVIEW -> reports.aiReport(interviewId).filter(r -> "released".equals(r.status()))
                        .map(ReportDtos.ReportView::recommendation).orElse(null);
                case PARTNER -> reports.partnerReport(interviewId).map(ReportDtos.Round2View::recommendation).orElse(null);
                case PANEL -> {
                    var report = panels.report(interviewId);
                    yield report.submitted() == 0 ? null : report.combined();
                }
            };
        }
        if (found == null && required) {
            throw ApiException.conflict("report_not_ready", kind == null ? "This round's report isn't in yet."
                    : switch (kind) {
                        case AI_INTERVIEW -> "The AI interview report hasn't been released by the ETT reviewer yet.";
                        case PARTNER -> "The partner assessment centre hasn't sent its report yet.";
                        case PANEL -> "No panel member has sent their review yet.";
                    });
        }
        return found;
    }

    private void recordReview(UUID applicationId, ApplicationStage stage, String outcome, String note) {
        jdbc.sql("""
                insert into report_reviews (application_id, report_type, outcome, note, reviewed_by, created_by)
                values (:a, :t, :o, :n, :u, :u)
                """)
                .param("a", applicationId).param("t", stage.name().toLowerCase(Locale.ROOT)).param("o", outcome)
                .param("n", note).param("u", CurrentUser.id()).update();
    }

    /**
     * Undoes a round decision made in the last 5 minutes: the candidate goes back to where they were and the email
     * about the decision, which hasn't gone yet, is cancelled. Not possible once the email has gone or something
     * else has happened since (for example the next round was booked).
     */
    @Transactional
    public List<StageDecisionView> undo(UUID decisionId) {
        StageDecision decision = decisions.findById(decisionId).orElseThrow(() -> ApiException.notFound("Decision"));
        JobApplication application = tenancy.application(decision.getApplicationId());
        java.time.Instant now = clock.instant();
        if (decision.getDecidedAt() == null || decision.getDecidedAt().plus(UNDO_WINDOW).isBefore(now)) {
            throw ApiException.conflict("undo_expired", "A decision can only be undone in the first 5 minutes.");
        }
        UUID newest = jdbc.sql("""
                        select id from stage_decisions where application_id = :a order by created_at desc limit 1
                        """)
                .param("a", application.getId()).query(UUID.class).single();
        boolean laterInterview = jdbc.sql("""
                        select exists (select 1 from interviews where application_id = :a and created_at > :at
                                          and status <> 'draft')
                        """)
                .param("a", application.getId()).param("at", java.sql.Timestamp.from(decision.getDecidedAt()))
                .query(Boolean.class).single();
        boolean stillThere = switch (decision.getDecision()) {
            case REJECT -> application.getStatus() == com.epictalenttrust.hr.application.ApplicationStatus.REJECTED;
            case ADVANCE -> application.isActive() && application.getStage() != decision.getStage();
            case HOLD -> application.isActive() && application.getStage() == decision.getStage();
        };
        if (!newest.equals(decisionId) || laterInterview || !stillThere) {
            throw ApiException.conflict("undo_too_late",
                    "Something has already happened since this decision (for example the next round was booked), so it can't be undone.");
        }
        if (decision.getMessageId() != null && !messages.cancelPending(decision.getMessageId())) {
            throw ApiException.conflict("email_sent", "The email to the candidate has already gone, so this can't be undone.");
        }
        ApplicationStage back = decision.getStageBefore() != null ? decision.getStageBefore() : decision.getStage();
        if (decision.getDecision() != StageDecisionType.HOLD) {
            application.restore(back, decision.getEnteredBefore() != null ? decision.getEnteredBefore()
                    : application.getStageEnteredAt());
        }
        jdbc.sql("""
                delete from report_reviews where id = (select id from report_reviews
                 where application_id = :a and report_type = :t and created_at >= :at order by created_at desc limit 1)
                """)
                .param("a", application.getId()).param("t", decision.getStage().name().toLowerCase(Locale.ROOT))
                .param("at", java.sql.Timestamp.from(decision.getDecidedAt())).update();
        decisions.delete(decision);
        decisions.flush();
        activity.record("stage.decision_undone", "application", application.getId());
        return reports.decisions(application.getId());
    }

    /** The candidate challenged a decision (they replied by email); a person must review it. */
    @Transactional
    public List<StageDecisionView> recordChallenge(UUID decisionId) {
        StageDecision decision = decisions.findById(decisionId).orElseThrow(() -> ApiException.notFound("Decision"));
        tenancy.application(decision.getApplicationId());
        if (decision.getChallengedAt() == null) {
            decision.markChallenged(clock.instant());
            activity.record("stage.challenged", "application", decision.getApplicationId());
        }
        return reports.decisions(decision.getApplicationId());
    }
}
