package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** One row of a job's interview plan. */
@Entity
@Table(name = "job_rounds")
public class JobRound extends AuditedEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(nullable = false)
    private int round;

    private InterviewMode mode;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private RoundKind kind;

    @Column(name = "target_days")
    private Integer targetDays;

    protected JobRound() {
    }

    public JobRound(UUID jobId, int round, String name, RoundKind kind, InterviewMode mode, Integer durationMin,
                    Integer targetDays) {
        this.jobId = jobId;
        this.round = round;
        update(name, kind, mode, durationMin, targetDays);
    }

    public void update(String name, RoundKind kind, InterviewMode mode, Integer durationMin, Integer targetDays) {
        this.name = name;
        this.kind = kind;
        this.mode = kind == RoundKind.PARTNER ? InterviewMode.IN_PERSON
                : kind == RoundKind.AI_INTERVIEW ? InterviewMode.ONLINE : mode;
        this.durationMin = durationMin;
        this.targetDays = targetDays;
        this.required = true;
    }

    /** Moves the round to another position when HR reorders the plan. */
    public void renumber(int round) {
        this.round = round;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getRound() {
        return round;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public boolean isRequired() {
        return required;
    }

    public String getName() {
        return name;
    }

    public RoundKind getKind() {
        return kind;
    }

    public Integer getTargetDays() {
        return targetDays;
    }
}
