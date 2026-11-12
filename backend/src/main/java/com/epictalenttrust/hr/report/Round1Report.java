package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** AI draft scores checked by an ETT reviewer. HR sees it once released. */
@Entity
@Table(name = "round1_reports")
public class Round1Report extends AuditedEntity {

    @Column(name = "interview_id", nullable = false, updatable = false)
    private UUID interviewId;

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "ai_overall")
    private Integer aiOverall;

    @Column(name = "final_overall")
    private Integer finalOverall;

    private Recommendation recommendation;

    @Column(name = "task_summary")
    private String taskSummary;

    @Column(name = "integrity_status")
    private IntegrityStatus integrityStatus;

    @Column(name = "reviewer_notes")
    private String reviewerNotes;

    @Column(nullable = false)
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(name = "released_at")
    private Instant releasedAt;

    protected Round1Report() {
    }

    public Round1Report(UUID interviewId, UUID reviewerId, Integer aiOverall, Integer finalOverall,
                        Recommendation recommendation, String taskSummary, IntegrityStatus integrityStatus,
                        String reviewerNotes) {
        this.interviewId = interviewId;
        this.reviewerId = reviewerId;
        this.aiOverall = aiOverall;
        this.finalOverall = finalOverall;
        this.recommendation = recommendation;
        this.taskSummary = taskSummary;
        this.integrityStatus = integrityStatus;
        this.reviewerNotes = reviewerNotes;
    }

    public void release(Instant at) {
        this.status = ReportStatus.RELEASED;
        this.releasedAt = at;
    }

    public UUID getInterviewId() {
        return interviewId;
    }

    public ReportStatus getStatus() {
        return status;
    }
}
