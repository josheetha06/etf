package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.activity.ActorKind;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.job.JobService;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.screening.ScreeningService;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Scores the CVs already in a job again, after HR changed its criteria. Earlier scores stay in the history. */
@Service
public class RescoreService {

    private static final Logger log = LoggerFactory.getLogger(RescoreService.class);

    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final CandidateRepository candidates;
    private final FileStorage storage;
    private final HiddenTextDetector hiddenText;
    private final ScreeningService screening;
    private final NotificationService notifications;
    private final TransactionTemplate transaction;

    public RescoreService(JobRepository jobs, JobApplicationRepository applications, CandidateRepository candidates,
                          FileStorage storage, HiddenTextDetector hiddenText, ScreeningService screening,
                          NotificationService notifications, PlatformTransactionManager transactionManager) {
        this.jobs = jobs;
        this.applications = applications;
        this.candidates = candidates;
        this.storage = storage;
        this.hiddenText = hiddenText;
        this.screening = screening;
        this.notifications = notifications;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCriteriaChanged(JobService.CriteriaChanged event) {
        ActorContext.Actor actor = event.changedBy() == null ? ActorContext.Actor.SYSTEM
                : new ActorContext.Actor(ActorKind.HR_USER, event.changedBy());
        ActorContext.runAs(actor, () -> rescoreJob(event.jobId(), event.changedBy()));
    }

    public int rescoreJob(UUID jobId, UUID requestedBy) {
        Job job = jobs.findById(jobId).orElseThrow();
        List<UUID> ids = applications.findByJobId(jobId).stream()
                .filter(a -> a.getCvTextKey() != null).map(JobApplication::getId).toList();
        int done = 0;
        for (UUID id : ids) {
            try {
                transaction.executeWithoutResult(status -> rescore(id, job));
                done++;
            } catch (RuntimeException ex) {
                log.error("Could not re-score application {}", id, ex);
            }
        }
        int scored = done;
        transaction.executeWithoutResult(status -> notifications.notify(new NotificationService.Request(job.getCompanyId(),
                List.of(requestedBy == null ? notifications.jobOwner(jobId) : requestedBy, notifications.jobOwner(jobId)),
                NotificationKind.CV_BATCH_DONE, "CVs re-scored for " + job.getTitle(),
                scored + " CVs were scored again with the new criteria. Earlier scores are in each candidate's history.",
                "/jobs/" + jobId + "?tab=shortlist", jobId, null, null)));
        return scored;
    }

    private void rescore(UUID applicationId, Job job) {
        JobApplication application = applications.findById(applicationId).orElseThrow();
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        String text = new String(storage.get(application.getCvTextKey()), StandardCharsets.UTF_8);
        String hidden = null;
        if (application.getCvFileKey() != null && application.getCvFileKey().endsWith(".pdf")) {
            hidden = hiddenText.hiddenText(storage.get(application.getCvFileKey())).orElse(null);
        }
        screening.screen(application, job, new ScreeningService.CvText(text,
                new CandidateDetails(candidate.getFullName(), candidate.getEmail(), candidate.getMobile(),
                        candidate.getCity(), candidate.getCountry()), hidden));
    }
}
