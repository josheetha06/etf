package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** HR's decision after an interview round. A person always decides; reasons are kept for challenges. */
@Entity
@Table(name = "stage_decisions")
public class StageDecision extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private ApplicationStage stage;

    @Column(nullable = false)
    private StageDecisionType decision;

    private String reason;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "challenged_at")
    private Instant challengedAt;

    /** The candidate's email about this decision, which waits a few minutes so the decision can be undone. */
    @Column(name = "message_id")
    private UUID messageId;

    /** Where the candidate was before, so an undo can put them back. */
    @Column(name = "stage_before")
    private ApplicationStage stageBefore;

    @Column(name = "entered_before")
    private Instant enteredBefore;

    protected StageDecision() {
    }

    public StageDecision(UUID applicationId, ApplicationStage stage, StageDecisionType decision, String reason,
                         UUID decidedBy, Instant decidedAt) {
        this.applicationId = applicationId;
        this.stage = stage;
        this.decision = decision;
        this.reason = reason;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public void rememberBefore(ApplicationStage stage, Instant enteredAt) {
        this.stageBefore = stage;
        this.enteredBefore = enteredAt;
    }

    public void attachMessage(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public ApplicationStage getStageBefore() {
        return stageBefore;
    }

    public Instant getEnteredBefore() {
        return enteredBefore;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void markChallenged(Instant at) {
        this.challengedAt = at;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public ApplicationStage getStage() {
        return stage;
    }

    public StageDecisionType getDecision() {
        return decision;
    }

    public Instant getChallengedAt() {
        return challengedAt;
    }
}
