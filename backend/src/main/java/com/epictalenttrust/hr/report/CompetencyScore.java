package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "competency_scores")
public class CompetencyScore extends AuditedEntity {

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(nullable = false)
    private String competency;

    @Column(name = "ai_draft_score")
    private Integer aiDraftScore;

    @Column(name = "final_score")
    private Integer finalScore;

    @Column(name = "reviewer_comment")
    private String reviewerComment;

    protected CompetencyScore() {
    }

    public CompetencyScore(UUID reportId, String competency, Integer aiDraftScore, Integer finalScore,
                           String reviewerComment) {
        this.reportId = reportId;
        this.competency = competency;
        this.aiDraftScore = aiDraftScore;
        this.finalScore = finalScore;
        this.reviewerComment = reviewerComment;
    }
}
