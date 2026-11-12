package com.epictalenttrust.hr.shortlist;

import com.epictalenttrust.hr.common.AuditedEntity;
import com.epictalenttrust.hr.screening.AiSuggestion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** HR's shortlist decision, stored next to the AI's suggestion at the time. Rows are history; the newest counts. */
@Entity
@Table(name = "shortlist_decisions")
public class ShortlistDecision extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "ai_suggestion")
    private AiSuggestion aiSuggestion;

    @Column(nullable = false)
    private ShortlistDecisionType decision;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    private String reason;

    protected ShortlistDecision() {
    }

    public ShortlistDecision(UUID applicationId, AiSuggestion aiSuggestion, ShortlistDecisionType decision,
                             UUID decidedBy, Instant decidedAt) {
        this.applicationId = applicationId;
        this.aiSuggestion = aiSuggestion;
        this.decision = decision;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public ShortlistDecision(UUID applicationId, AiSuggestion aiSuggestion, ShortlistDecisionType decision,
                             UUID decidedBy, Instant decidedAt, String reason) {
        this(applicationId, aiSuggestion, decision, decidedBy, decidedAt);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public AiSuggestion getAiSuggestion() {
        return aiSuggestion;
    }

    public ShortlistDecisionType getDecision() {
        return decision;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
