package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** Whether one criterion was found in the CV, with the line that shows it. */
@Entity
@Table(name = "match_evidence")
public class MatchEvidence extends AuditedEntity {

    @Column(name = "screening_id", nullable = false, updatable = false)
    private UUID screeningId;

    @Column(name = "criterion_id", nullable = false, updatable = false)
    private UUID criterionId;

    @Column(nullable = false)
    private boolean matched;

    @Column(name = "cv_quote")
    private String cvQuote;

    @Column(name = "flagged_by")
    private UUID flaggedBy;

    @Column(name = "flag_note")
    private String flagNote;

    private EvidenceStrength strength;

    private Double points;

    @Column(name = "cv_section")
    private String cvSection;

    @Column(name = "matched_term")
    private String matchedTerm;

    protected MatchEvidence() {
    }

    public MatchEvidence(UUID screeningId, UUID criterionId, boolean matched, String cvQuote) {
        this.screeningId = screeningId;
        this.criterionId = criterionId;
        this.matched = matched;
        this.cvQuote = cvQuote;
    }

    public MatchEvidence(UUID screeningId, ScreeningOutcome.CriterionResult result) {
        this(screeningId, result.criterionId(), result.matched(), result.quote());
        this.strength = result.strength();
        this.points = result.points();
        this.cvSection = result.section();
        this.matchedTerm = result.matchedTerm();
    }

    public void flag(UUID hrUserId, String note) {
        this.flaggedBy = hrUserId;
        this.flagNote = note;
    }

    public UUID getScreeningId() {
        return screeningId;
    }

    public UUID getCriterionId() {
        return criterionId;
    }

    public boolean isMatched() {
        return matched;
    }

    public String getCvQuote() {
        return cvQuote;
    }

    public UUID getFlaggedBy() {
        return flaggedBy;
    }

    public String getFlagNote() {
        return flagNote;
    }
}
