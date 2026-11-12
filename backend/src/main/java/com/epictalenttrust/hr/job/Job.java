package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jobs")
public class Job extends AuditedEntity {

    @Column(name = "job_ref", nullable = false, updatable = false)
    private String jobRef;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String title;

    private String location;

    @Column(name = "contract_type")
    private ContractType contractType;

    private Integer openings;

    @Column(name = "min_experience_yrs")
    private Integer minExperienceYrs;

    @Column(nullable = false)
    private JobStatus status = JobStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "shortlist_confirmed_at")
    private Instant shortlistConfirmedAt;

    @Column(name = "criteria_version", nullable = false)
    private int criteriaVersion = 1;

    @Column(name = "max_notice_days")
    private Integer maxNoticeDays;

    @Column(name = "max_ctc_lpa")
    private Double maxCtcLpa;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "accepted_locations", columnDefinition = "text[]")
    private List<String> acceptedLocations;

    @Column(name = "ai_shortlisting", nullable = false)
    private boolean aiShortlisting = true;

    private String description;

    @Column(name = "description_file_key")
    private String descriptionFileKey;

    @Column(name = "description_file_name")
    private String descriptionFileName;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    protected Job() {
    }

    public Job(String jobRef, UUID companyId) {
        this.jobRef = jobRef;
        this.companyId = companyId;
    }

    public void describe(String title, String location, ContractType contractType, Integer openings, Integer minExperienceYrs) {
        this.title = title;
        this.location = location;
        this.contractType = contractType;
        this.openings = openings;
        this.minExperienceYrs = minExperienceYrs;
    }

    /** Limits used to flag candidates (never to score them). */
    public void setLimits(Integer maxNoticeDays, Double maxCtcLpa, List<String> acceptedLocations) {
        this.maxNoticeDays = maxNoticeDays;
        this.maxCtcLpa = maxCtcLpa;
        this.acceptedLocations = acceptedLocations == null || acceptedLocations.isEmpty() ? null : acceptedLocations;
    }

    public int nextCriteriaVersion() {
        criteriaVersion++;
        return criteriaVersion;
    }

    /** HR finished reviewing the AI shortlisting report. Candidates move on one by one, not all at once. */
    public void finishShortlistReview(Instant at) {
        shortlistConfirmedAt = at;
        if (status == JobStatus.OPEN) {
            status = JobStatus.SHORTLIST_CONFIRMED;
        }
    }

    public int getCriteriaVersion() {
        return criteriaVersion;
    }

    public Integer getMaxNoticeDays() {
        return maxNoticeDays;
    }

    public Double getMaxCtcLpa() {
        return maxCtcLpa;
    }

    public List<String> getAcceptedLocations() {
        return acceptedLocations == null ? List.of() : acceptedLocations;
    }

    public void publish(Instant at) {
        status = JobStatus.OPEN;
        publishedAt = at;
    }

    public void confirmShortlist(Instant at) {
        status = JobStatus.SHORTLIST_CONFIRMED;
        shortlistConfirmedAt = at;
    }

    public void close() {
        status = JobStatus.CLOSED;
    }

    /** On hold, closed or reopened, with HR's reason. Reopening returns the job to where it was. */
    public void changeStatus(JobStatus next, String reason, UUID by, Instant at) {
        status = next;
        statusReason = reason;
        statusChangedBy = by;
        statusChangedAt = at;
    }

    public void setAiShortlisting(boolean aiShortlisting) {
        this.aiShortlisting = aiShortlisting;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void attachDescriptionFile(String key, String name) {
        this.descriptionFileKey = key;
        this.descriptionFileName = name;
    }

    public void setHiringManagerId(UUID hiringManagerId) {
        this.hiringManagerId = hiringManagerId;
    }

    public boolean isAiShortlisting() {
        return aiShortlisting;
    }

    public String getDescription() {
        return description;
    }

    public String getDescriptionFileKey() {
        return descriptionFileKey;
    }

    public String getDescriptionFileName() {
        return descriptionFileName;
    }

    public UUID getHiringManagerId() {
        return hiringManagerId;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public UUID getStatusChangedBy() {
        return statusChangedBy;
    }

    public String getJobRef() {
        return jobRef;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getTitle() {
        return title;
    }

    public String getLocation() {
        return location;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public Integer getOpenings() {
        return openings;
    }

    public Integer getMinExperienceYrs() {
        return minExperienceYrs;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getShortlistConfirmedAt() {
        return shortlistConfirmedAt;
    }
}
