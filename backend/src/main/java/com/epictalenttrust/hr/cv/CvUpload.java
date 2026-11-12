package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.application.ApplicationSource;
import com.epictalenttrust.hr.common.AuditedEntity;
import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One CV handed to the portal, from upload until it is added to the job or refused (table "cv_uploads"). */
@Entity
@Table(name = "cv_uploads")
public class CvUpload extends AuditedEntity {

    public enum Status {
        QUEUED, READING, NEEDS_DETAILS, ADDED, DUPLICATE, FAILED;

        @Converter(autoApply = true)
        public static class DbConverter extends PgEnumConverter<Status> {
            public DbConverter() {
                super(Status.class);
            }
        }
    }

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "text_key")
    private String textKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(nullable = false)
    private Status status = Status.QUEUED;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "detected_name")
    private String detectedName;

    @Column(name = "detected_email")
    private String detectedEmail;

    @Column(name = "detected_mobile")
    private String detectedMobile;

    @Column(name = "detected_city")
    private String detectedCity;

    @Column(name = "detected_country")
    private String detectedCountry;

    private ApplicationSource source;

    @Column(name = "source_detail")
    private String sourceDetail;

    @Column(name = "application_id")
    private UUID applicationId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected CvUpload() {
    }

    public CvUpload(UUID jobId, UUID batchId, String fileName, ApplicationSource source, String sourceDetail,
                    UUID uploadedBy, Instant queuedAt) {
        this.jobId = jobId;
        this.batchId = batchId;
        this.fileName = fileName;
        this.source = source;
        this.sourceDetail = sourceDetail;
        this.uploadedBy = uploadedBy;
        this.queuedAt = queuedAt;
    }

    public void stored(String fileKey) {
        this.fileKey = fileKey;
    }

    public void refuse(String code, String message, Instant at) {
        this.status = Status.FAILED;
        this.errorCode = code;
        this.errorMessage = message;
        this.finishedAt = at;
    }

    public void needsDetails(String textKey, String contentType, CandidateDetails details, String message, Instant at) {
        this.status = Status.NEEDS_DETAILS;
        this.textKey = textKey;
        this.contentType = contentType;
        detected(details);
        this.errorCode = "no_email";
        this.errorMessage = message;
        this.finishedAt = at;
    }

    public void added(UUID applicationId, CandidateDetails details, Instant at) {
        this.status = Status.ADDED;
        this.applicationId = applicationId;
        detected(details);
        this.errorCode = null;
        this.errorMessage = null;
        this.finishedAt = at;
    }

    public void duplicate(UUID applicationId, String message, Instant at) {
        this.status = Status.DUPLICATE;
        this.applicationId = applicationId;
        this.errorCode = "duplicate";
        this.errorMessage = message;
        this.finishedAt = at;
    }

    /** Claimed by the worker. */
    public void startReading(Instant at) {
        this.status = Status.READING;
        this.attempts++;
        this.startedAt = at;
    }

    public void retryLater() {
        this.status = Status.QUEUED;
    }

    private void detected(CandidateDetails details) {
        if (details == null) {
            return;
        }
        this.detectedName = details.fullName();
        this.detectedEmail = details.email();
        this.detectedMobile = details.mobile();
        this.detectedCity = details.city();
        this.detectedCountry = details.country();
    }

    public CandidateDetails detectedDetails() {
        return new CandidateDetails(detectedName, detectedEmail, detectedMobile, detectedCity, detectedCountry);
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getTextKey() {
        return textKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Status getStatus() {
        return status;
    }

    public ApplicationSource getSource() {
        return source;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isFinished() {
        return status != Status.QUEUED && status != Status.READING;
    }
}
