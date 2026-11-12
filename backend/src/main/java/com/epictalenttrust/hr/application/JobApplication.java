package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One candidate in one job (table "applications"). Holds the CV HR uploaded for that job. */
@Entity
@Table(name = "applications")
public class JobApplication extends AuditedEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    private ApplicationSource source;

    @Column(name = "cv_file_key")
    private String cvFileKey;

    @Column(name = "cv_text_key")
    private String cvTextKey;

    @Column(name = "cv_status")
    private CvStatus cvStatus;

    @Column(name = "cv_delete_after")
    private LocalDate cvDeleteAfter;

    @Column(name = "cv_uploaded_by")
    private UUID cvUploadedBy;

    @Column(nullable = false)
    private ApplicationStage stage = ApplicationStage.SCREENING;

    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.ACTIVE;

    @Column(name = "source_detail")
    private String sourceDetail;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "cv_text_hash")
    private String cvTextHash;

    @Column(name = "stage_entered_at")
    private Instant stageEnteredAt;

    @Column(name = "due_reminder_sent_at")
    private Instant dueReminderSentAt;

    protected JobApplication() {
    }

    public JobApplication(UUID jobId, UUID candidateId, ApplicationSource source, UUID uploadedBy) {
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.source = source;
        this.cvUploadedBy = uploadedBy;
        this.cvStatus = CvStatus.UPLOADED;
    }

    public void attachCv(String fileKey, String textKey, LocalDate deleteAfter) {
        this.cvFileKey = fileKey;
        this.cvTextKey = textKey;
        this.cvDeleteAfter = deleteAfter;
        this.cvStatus = CvStatus.PARSED;
    }

    public void markCvDeleted() {
        this.cvFileKey = null;
        this.cvTextKey = null;
        this.cvStatus = CvStatus.DELETED;
    }

    public void moveTo(ApplicationStage stage) {
        if (this.stage != stage) {
            this.stageEnteredAt = Instant.now();
            this.dueReminderSentAt = null;
        }
        this.stage = stage;
    }

    /** Puts the candidate back where they were, when HR undoes a decision. */
    public void restore(ApplicationStage stage, Instant enteredAt) {
        this.status = ApplicationStatus.ACTIVE;
        this.statusReason = null;
        this.statusChangedAt = null;
        this.stage = stage;
        this.stageEnteredAt = enteredAt;
    }

    public Instant getStageEnteredAt() {
        return stageEnteredAt;
    }

    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public void reject(String reason, Instant at) {
        this.status = ApplicationStatus.REJECTED;
        this.statusReason = reason;
        this.statusChangedAt = at;
    }

    /** The candidate withdrew; reminders stop and the reason is kept for reports. */
    public void withdraw(String reason, Instant at) {
        this.status = ApplicationStatus.WITHDRAWN;
        this.statusReason = reason;
        this.statusChangedAt = at;
    }

    /** Offer accepted and the role filled. */
    public void markHired(Instant at) {
        this.status = ApplicationStatus.HIRED;
        this.statusChangedAt = at;
    }

    public void setSourceDetail(String sourceDetail) {
        this.sourceDetail = sourceDetail;
    }

    public void setCvTextHash(String cvTextHash) {
        this.cvTextHash = cvTextHash;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public boolean isActive() {
        return status == ApplicationStatus.ACTIVE;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public ApplicationSource getSource() {
        return source;
    }

    public String getCvFileKey() {
        return cvFileKey;
    }

    public String getCvTextKey() {
        return cvTextKey;
    }

    public CvStatus getCvStatus() {
        return cvStatus;
    }

    public LocalDate getCvDeleteAfter() {
        return cvDeleteAfter;
    }

    public UUID getCvUploadedBy() {
        return cvUploadedBy;
    }

    public ApplicationStage getStage() {
        return stage;
    }

    public ApplicationStatus getStatus() {
        return status;
    }
}
