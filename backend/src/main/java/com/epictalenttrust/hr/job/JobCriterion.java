package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** A shortlisting criterion: a must-have or nice-to-have keyword, or a certification. */
@Entity
@Table(name = "job_criteria")
public class JobCriterion extends AuditedEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false)
    private CriterionKind kind;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "min_years")
    private Integer minYears;

    protected JobCriterion() {
    }

    public JobCriterion(UUID jobId, CriterionKind kind, String value) {
        this.jobId = jobId;
        this.kind = kind;
        this.value = value;
    }

    public JobCriterion(UUID jobId, CriterionKind kind, String value, Integer minYears, int version) {
        this(jobId, kind, value);
        this.minYears = kind == CriterionKind.MUST_HAVE ? minYears : null;
        this.version = version;
    }

    /** Replaced by newer criteria; kept so earlier scores keep their evidence. */
    public void retire(Instant at) {
        this.retiredAt = at;
    }

    public int getVersion() {
        return version;
    }

    public Instant getRetiredAt() {
        return retiredAt;
    }

    public Integer getMinYears() {
        return minYears;
    }

    public UUID getJobId() {
        return jobId;
    }

    public CriterionKind getKind() {
        return kind;
    }

    public String getValue() {
        return value;
    }
}
