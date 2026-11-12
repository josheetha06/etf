package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.common.ErrorLogWriter;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Works through queued CVs. Starts as soon as uploads are saved and is also polled every few seconds, so a
 * restart never leaves CVs waiting. Rows are claimed with SKIP LOCKED, so several workers never take the same CV.
 */
@Component
public class CvIntakeWorker {

    static final Duration TARGET = Duration.ofMinutes(10);
    private static final Duration STUCK = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(CvIntakeWorker.class);

    private final JdbcClient jdbc;
    private final CvIntakeService intake;
    private final NotificationService notifications;
    private final ErrorLogWriter errorLog;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public CvIntakeWorker(JdbcClient jdbc, CvIntakeService intake, NotificationService notifications,
                          ErrorLogWriter errorLog, PlatformTransactionManager transactionManager, Clock clock) {
        this.jdbc = jdbc;
        this.intake = intake;
        this.notifications = notifications;
        this.errorLog = errorLog;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(CvIntakeService.Queued event) {
        drain();
    }

    /** Processes queued CVs until none are left. */
    public void drain() {
        UUID id;
        while ((id = claim()) != null) {
            intake.process(id);
            batchFinished(id);
        }
    }

    private UUID claim() {
        return transaction.execute(status -> jdbc.sql("""
                        update cv_uploads set status = 'reading', started_at = :now, attempts = attempts + 1
                         where id = (select id from cv_uploads where status = 'queued'
                                      order by queued_at for update skip locked limit 1)
                        returning id
                        """)
                .param("now", Timestamp.from(clock.instant()))
                .query(UUID.class).optional().orElse(null));
    }

    /** When the last CV of a batch is done, tell the uploader and the job owner the shortlisting report is ready. */
    private void batchFinished(UUID uploadId) {
        transaction.executeWithoutResult(status -> {
            record Batch(UUID batchId, UUID jobId, UUID companyId, String jobTitle, UUID uploadedBy) {
            }
            Batch batch = jdbc.sql("""
                            select u.batch_id, u.job_id, j.company_id, j.title, u.uploaded_by
                              from cv_uploads u join jobs j on j.id = u.job_id where u.id = :id
                            """)
                    .param("id", uploadId)
                    .query((rs, row) -> new Batch(Rows.uuid(rs, "batch_id"), Rows.uuid(rs, "job_id"),
                            Rows.uuid(rs, "company_id"), rs.getString("title"), Rows.uuid(rs, "uploaded_by")))
                    .optional().orElse(null);
            if (batch == null) {
                return;
            }
            // One worker per batch gets to announce it.
            Boolean locked = jdbc.sql("select pg_try_advisory_xact_lock(hashtext(:b))")
                    .param("b", batch.batchId().toString()).query(Boolean.class).single();
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            record Counts(int open, int added, int details, int refused, int total) {
            }
            Counts counts = jdbc.sql("""
                            select count(*) filter (where status in ('queued', 'reading')) as open,
                                   count(*) filter (where status = 'added') as added,
                                   count(*) filter (where status = 'needs_details') as details,
                                   count(*) filter (where status in ('failed', 'duplicate')) as refused,
                                   count(*) as total
                              from cv_uploads where batch_id = :b
                            """)
                    .param("b", batch.batchId())
                    .query((rs, row) -> new Counts(rs.getInt("open"), rs.getInt("added"), rs.getInt("details"),
                            rs.getInt("refused"), rs.getInt("total")))
                    .single();
            String link = "/jobs/" + batch.jobId() + "?tab=shortlist&batch=" + batch.batchId();
            boolean announced = jdbc.sql("select exists (select 1 from notifications where kind = 'cv_batch_done' and link = :l)")
                    .param("l", link).query(Boolean.class).single();
            if (counts.open() > 0 || announced || counts.total() < 1) {
                return;
            }
            String body = counts.added() + " added and scored"
                    + (counts.details() > 0 ? ", " + counts.details() + " need details" : "")
                    + (counts.refused() > 0 ? ", " + counts.refused() + " not added" : "")
                    + ". The AI shortlisting report is ready to review.";
            notifications.notify(new NotificationService.Request(batch.companyId(),
                    List.of(batch.uploadedBy() == null ? notifications.jobOwner(batch.jobId()) : batch.uploadedBy(),
                            notifications.jobOwner(batch.jobId())),
                    NotificationKind.CV_BATCH_DONE, "CVs screened for " + batch.jobTitle(), body, link,
                    batch.jobId(), null, null));
        });
    }

    /** CVs not done within 10 minutes: alert HR (and the error log for ETT); put stuck ones back in the queue. */
    public void checkOverdue() {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("""
                    update cv_uploads set status = 'queued'
                     where status = 'reading' and started_at < :stuck and attempts < :max
                    """)
                    .param("stuck", Timestamp.from(clock.instant().minus(STUCK)))
                    .param("max", CvIntakeService.MAX_ATTEMPTS).update();
            jdbc.sql("""
                    update cv_uploads set status = 'failed', error_code = 'processing_failed',
                           error_message = 'This CV couldn''t be processed. Try uploading it again.', finished_at = :now
                     where status = 'reading' and started_at < :stuck and attempts >= :max
                    """)
                    .param("now", Timestamp.from(clock.instant()))
                    .param("stuck", Timestamp.from(clock.instant().minus(STUCK)))
                    .param("max", CvIntakeService.MAX_ATTEMPTS).update();
        });
        record Late(UUID jobId, UUID companyId, String title, UUID uploadedBy, int count) {
        }
        List<Late> late = jdbc.sql("""
                        select u.job_id, j.company_id, j.title, u.uploaded_by, count(*) as n
                          from cv_uploads u join jobs j on j.id = u.job_id
                         where u.status in ('queued', 'reading') and u.queued_at < :limit and u.overdue_alerted_at is null
                         group by u.job_id, j.company_id, j.title, u.uploaded_by
                        """)
                .param("limit", Timestamp.from(clock.instant().minus(TARGET)))
                .query((rs, row) -> new Late(Rows.uuid(rs, "job_id"), Rows.uuid(rs, "company_id"), rs.getString("title"),
                        Rows.uuid(rs, "uploaded_by"), rs.getInt("n")))
                .list();
        for (Late item : late) {
            log.error("{} CVs for job {} not screened within {} minutes", item.count(), item.jobId(), TARGET.toMinutes());
            errorLog.record("error", "cv-intake-overdue",
                    new IllegalStateException(item.count() + " CVs not screened within 10 minutes for job " + item.jobId()), null);
            transaction.executeWithoutResult(status -> {
                notifications.notify(new NotificationService.Request(item.companyId(),
                        List.of(item.uploadedBy() == null ? notifications.jobOwner(item.jobId()) : item.uploadedBy(),
                                notifications.jobOwner(item.jobId())),
                        NotificationKind.CV_OVERDUE, "CV screening is taking longer than usual for " + item.title(),
                        item.count() + " CVs are still waiting. ETT has been alerted and is looking into it.",
                        "/jobs/" + item.jobId() + "?tab=cvs", item.jobId(), null, null));
                jdbc.sql("""
                        update cv_uploads set overdue_alerted_at = :now
                         where job_id = :job and status in ('queued', 'reading') and overdue_alerted_at is null
                        """)
                        .param("now", Timestamp.from(clock.instant())).param("job", item.jobId()).update();
            });
        }
    }
}
