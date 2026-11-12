package com.epictalenttrust.hr.activity;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Operational logs: AI runs and CV access. Calls join the caller's transaction when there is one;
 * failures are logged after the failed transaction has rolled back, so they are kept.
 */
@Component
public class ProcessingLog {

    public enum AiTask { CV_PARSE, CV_SCREENING, ROUND1_SCORING, REPORT_DRAFT }

    public enum DocAction { VIEW, DOWNLOAD, DELETE, PURGE }

    private final JdbcClient jdbc;

    public ProcessingLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void aiRun(AiTask task, UUID applicationId, UUID screeningId, String modelVersion, boolean succeeded,
                      String errorMessage, Instant startedAt, Instant finishedAt) {
        jdbc.sql("""
                insert into ai_processing_log (task, application_id, screening_id, model_version, status,
                                               duration_ms, error_message, started_at, finished_at)
                values (:task, :applicationId, :screeningId, :model, :status, :duration, :error, :started, :finished)
                """)
                .param("task", task.name().toLowerCase())
                .param("applicationId", applicationId)
                .param("screeningId", screeningId)
                .param("model", modelVersion)
                .param("status", succeeded ? "succeeded" : "failed")
                .param("duration", (int) (finishedAt.toEpochMilli() - startedAt.toEpochMilli()))
                .param("error", errorMessage)
                .param("started", Timestamp.from(startedAt))
                .param("finished", Timestamp.from(finishedAt))
                .update();
    }

    @Transactional
    public void documentAccess(UUID applicationId, String fileKey, DocAction action, String actorKind, UUID actorId,
                               String ipAddress) {
        jdbc.sql("""
                insert into document_access_log (application_id, file_key, action, accessed_kind, accessed_by, ip_address)
                values (:applicationId, :fileKey, :action, :kind, :actorId, :ip)
                """)
                .param("applicationId", applicationId)
                .param("fileKey", fileKey)
                .param("action", action.name().toLowerCase())
                .param("kind", actorKind)
                .param("actorId", actorId)
                .param("ip", ipAddress)
                .update();
    }
}
