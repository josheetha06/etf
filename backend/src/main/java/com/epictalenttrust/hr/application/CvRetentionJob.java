package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.activity.ProcessingLog;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Deletes CV files and their text once cv_delete_after has passed, unless the candidate is
 * still in the process for an open job. Runs nightly.
 */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class CvRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(CvRetentionJob.class);

    private final JobApplicationRepository applications;
    private final FileStorage storage;
    private final ProcessingLog processingLog;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CvRetentionJob(JobApplicationRepository applications, FileStorage storage, ProcessingLog processingLog,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.applications = applications;
        this.storage = storage;
        this.processingLog = processingLog;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 2 * * *", zone = "UTC")
    public void purgeExpiredCvs() {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> transaction.executeWithoutResult(status -> {
            int purged = 0;
            for (JobApplication application : applications.findCvsDueForDeletion(LocalDate.now(clock))) {
                String fileKey = application.getCvFileKey();
                storage.delete(fileKey);
                if (application.getCvTextKey() != null) {
                    storage.delete(application.getCvTextKey());
                }
                application.markCvDeleted();
                processingLog.documentAccess(application.getId(), fileKey, ProcessingLog.DocAction.PURGE, "system", null, null);
                purged++;
            }
            if (purged > 0) {
                log.info("Deleted {} expired CVs", purged);
            }
        }));
    }
}
