package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** The AI agent's score for one CV against one job's criteria. Reruns add rows; the newest one counts. */
@Entity
@Table(name = "ai_screenings")
public class AiScreening extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "must_have_pts")
    private Integer mustHavePts;

    @Column(name = "nice_to_have_pts")
    private Integer niceToHavePts;

    @Column(name = "experience_pts")
    private Integer experiencePts;

    @Column(name = "certification_pts")
    private Integer certificationPts;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "certifications_found")
    private String certificationsFound;

    private AiSuggestion suggestion;

    @Column(name = "criteria_version")
    private Integer criteriaVersion;

    @Column(name = "total_years")
    private Double totalYears;

    @Column(name = "relevant_years")
    private Double relevantYears;

    private String summary;

    @Column(name = "reading_quality")
    private String readingQuality;

    @Column(name = "shortlist_cutoff")
    private Integer shortlistCutoff;

    @Column(name = "reserve_cutoff")
    private Integer reserveCutoff;

    private String reason;

    protected AiScreening() {
    }

    public AiScreening(UUID applicationId, String modelVersion, ScreeningOutcome outcome, int criteriaVersion,
                       int shortlistCutoff, int reserveCutoff) {
        this(applicationId, modelVersion, outcome);
        this.criteriaVersion = criteriaVersion;
        this.totalYears = outcome.totalYears();
        this.relevantYears = outcome.relevantYears();
        this.summary = outcome.summary();
        this.readingQuality = outcome.readable() ? "good" : "check";
        this.shortlistCutoff = shortlistCutoff;
        this.reserveCutoff = reserveCutoff;
        this.reason = outcome.reason();
    }

    public AiScreening(UUID applicationId, String modelVersion, ScreeningOutcome outcome) {
        this.applicationId = applicationId;
        this.modelVersion = modelVersion;
        this.matchScore = outcome.matchScore();
        this.mustHavePts = outcome.mustHavePts();
        this.niceToHavePts = outcome.niceToHavePts();
        this.experiencePts = outcome.experiencePts();
        this.certificationPts = outcome.certificationPts();
        this.experienceYears = outcome.experienceYears();
        this.certificationsFound = outcome.certificationsFound();
        this.suggestion = outcome.suggestion();
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public AiSuggestion getSuggestion() {
        return suggestion;
    }

    public Integer getCriteriaVersion() {
        return criteriaVersion;
    }

    public Double getTotalYears() {
        return totalYears;
    }

    public Double getRelevantYears() {
        return relevantYears;
    }

    public String getSummary() {
        return summary;
    }

    public String getReason() {
        return reason;
    }
}
