package com.epictalenttrust.hr.shortlist;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationQueries;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobStatus;
import com.epictalenttrust.hr.screening.AiScreening;
import com.epictalenttrust.hr.screening.AiScreeningRepository;
import com.epictalenttrust.hr.screening.AiSuggestion;
import com.epictalenttrust.hr.screening.MatchEvidence;
import com.epictalenttrust.hr.screening.MatchEvidenceRepository;
import com.epictalenttrust.hr.screening.ScreeningFlag;
import com.epictalenttrust.hr.screening.ScreeningFlagRepository;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * HR's shortlist decision on each candidate. The AI only suggests: going against it needs a short reason, and red
 * flags must be reviewed before someone is shortlisted. Shortlisting a candidate opens their Round 1 straight away.
 */
@Service
public class ShortlistService {

    private final Tenancy tenancy;
    private final AiScreeningRepository screenings;
    private final ShortlistDecisionRepository decisions;
    private final MatchEvidenceRepository evidence;
    private final ScreeningFlagRepository flags;
    private final ApplicationQueries queries;
    private final ActivityService activity;
    private final Clock clock;

    public ShortlistService(Tenancy tenancy, AiScreeningRepository screenings, ShortlistDecisionRepository decisions,
                            MatchEvidenceRepository evidence, ScreeningFlagRepository flags, ApplicationQueries queries,
                            ActivityService activity, Clock clock) {
        this.tenancy = tenancy;
        this.screenings = screenings;
        this.decisions = decisions;
        this.evidence = evidence;
        this.flags = flags;
        this.queries = queries;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public void decide(UUID applicationId, ShortlistDecisionType decision, String reason) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        if (job.getStatus() != JobStatus.OPEN && job.getStatus() != JobStatus.SHORTLIST_CONFIRMED) {
            throw ApiException.conflict("job_not_open", "Shortlist decisions can only change while the job is open.");
        }
        if (!application.isActive()) {
            throw ApiException.conflict("not_active", "This candidate is no longer in the process.");
        }
        if (application.getStage().ordinal() > ApplicationStage.SHORTLIST.ordinal()) {
            throw ApiException.conflict("already_interviewing",
                    "This candidate is already in the interview rounds. Use the round decision instead.");
        }
        AiScreening screening = screenings.findFirstByApplicationIdOrderByCreatedAtDesc(applicationId).orElse(null);
        AiSuggestion suggestion = screening == null ? null : screening.getSuggestion();
        if (decision == ShortlistDecisionType.SHORTLISTED && screening != null) {
            long openRed = flags.findByScreeningId(screening.getId()).stream()
                    .filter(f -> f.isRed() && !f.isReviewed()).count();
            if (openRed > 0) {
                throw ApiException.conflict("flags_not_reviewed",
                        "Review the " + openRed + " red flag" + (openRed == 1 ? "" : "s") + " before shortlisting.");
            }
        }
        String why = reason == null ? null : reason.strip();
        if (suggestion != null && !agrees(suggestion, decision) && (why == null || why.length() < 5)) {
            throw ApiException.badRequest("reason_required",
                    "You're going against the AI's suggestion, so add a short reason (at least 5 characters).");
        }
        decisions.save(new ShortlistDecision(applicationId, suggestion, decision, CurrentUser.id(), clock.instant(),
                why == null || why.isEmpty() ? null : why));
        application.moveTo(decision == ShortlistDecisionType.SHORTLISTED ? ApplicationStage.SHORTLIST : ApplicationStage.SCREENING);
        activity.record("shortlist.decided", "application", applicationId);
    }

    /** HR's view on one red flag or point to check. */
    @Transactional
    public void reviewFlag(UUID flagId, String outcome, String note) {
        ScreeningFlag flag = flags.findById(flagId).orElseThrow(() -> ApiException.notFound("Flag"));
        tenancy.application(flag.getApplicationId());
        if (!"fine".equals(outcome) && !"concern".equals(outcome)) {
            throw ApiException.badRequest("invalid_outcome", "Choose \"Checked, fine\" or \"Concern\".");
        }
        flag.review(outcome, note == null || note.isBlank() ? null : note.strip(), CurrentUser.id(), clock.instant());
        activity.record("flag.reviewed", "application", flag.getApplicationId());
    }

    /** HR has looked through the shortlisting report for this job. Candidates still move on one by one. */
    @Transactional
    public void finishReview(UUID jobId) {
        Job job = tenancy.job(jobId);
        if (job.getStatus() != JobStatus.OPEN && job.getStatus() != JobStatus.SHORTLIST_CONFIRMED) {
            throw ApiException.conflict("job_not_open", "Only an open job's shortlist can be reviewed.");
        }
        job.finishShortlistReview(clock.instant());
        activity.record("shortlist.confirmed", "job", jobId);
    }

    @Transactional
    public void flagEvidence(UUID evidenceId, String note) {
        UUID applicationId = queries.applicationForEvidence(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Evidence"));
        tenancy.application(applicationId);
        MatchEvidence item = evidence.findById(evidenceId).orElseThrow(() -> ApiException.notFound("Evidence"));
        item.flag(CurrentUser.id(), note == null || note.isBlank() ? null : note.strip());
        activity.record("match.flagged", "application", applicationId);
    }

    /** The AI's suggestion and HR's decision say the same thing. */
    public static boolean agrees(AiSuggestion suggestion, ShortlistDecisionType decision) {
        return switch (suggestion) {
            case SHORTLIST -> decision == ShortlistDecisionType.SHORTLISTED;
            case REVIEW -> decision == ShortlistDecisionType.HOLD;
            case REJECT -> decision == ShortlistDecisionType.REJECTED;
        };
    }
}
