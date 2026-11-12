package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** A red flag or point to check in one screening, and HR's review of it. */
@Entity
@Table(name = "screening_flags")
public class ScreeningFlag extends AuditedEntity {

    @Column(name = "screening_id", nullable = false, updatable = false)
    private UUID screeningId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String message;

    @Column(name = "cv_quote")
    private String cvQuote;

    @Column(name = "review_outcome")
    private String reviewOutcome;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected ScreeningFlag() {
    }

    public ScreeningFlag(UUID screeningId, UUID applicationId, FlagFinding finding) {
        this.screeningId = screeningId;
        this.applicationId = applicationId;
        this.severity = finding.severity().name().toLowerCase(Locale.ROOT);
        this.kind = finding.kind();
        this.message = finding.message();
        this.cvQuote = finding.cvQuote();
    }

    public void review(String outcome, String note, UUID hrUserId, Instant at) {
        this.reviewOutcome = outcome;
        this.reviewNote = note;
        this.reviewedBy = hrUserId;
        this.reviewedAt = at;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public boolean isRed() {
        return "red".equals(severity);
    }

    public boolean isReviewed() {
        return reviewOutcome != null;
    }

    public String getKind() {
        return kind;
    }
}
