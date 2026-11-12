package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A candidate asked for a different time. The original time stays held until HR replies. */
@Entity
@Table(name = "reschedule_requests")
public class RescheduleRequest extends AuditedEntity {

    @Column(name = "interview_id", nullable = false, updatable = false)
    private UUID interviewId;

    private RescheduleReason reason;

    private String note;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "available_days", columnDefinition = "date[]")
    private List<LocalDate> availableDays;

    @Column(name = "time_of_day")
    private TimeOfDay timeOfDay;

    @Column(nullable = false)
    private RescheduleStatus status = RescheduleStatus.PENDING;

    @Column(name = "new_start_at")
    private Instant newStartAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "new_slot_id")
    private UUID newSlotId;

    protected RescheduleRequest() {
    }

    public RescheduleRequest(UUID interviewId, RescheduleReason reason, String note, List<LocalDate> availableDays,
                             TimeOfDay timeOfDay) {
        this.interviewId = interviewId;
        this.reason = reason;
        this.note = note;
        this.availableDays = availableDays;
        this.timeOfDay = timeOfDay;
    }

    public void approve(Instant newStartAt, UUID hrUserId) {
        this.status = RescheduleStatus.APPROVED;
        this.newStartAt = newStartAt;
        this.resolvedBy = hrUserId;
    }

    /** Round 2: the candidate gets a new seat at a partner centre. */
    public void approveWithSlot(Instant newStartAt, UUID slotId, UUID hrUserId) {
        approve(newStartAt, hrUserId);
        this.newSlotId = slotId;
    }

    public UUID getNewSlotId() {
        return newSlotId;
    }

    public void decline(UUID hrUserId) {
        this.status = RescheduleStatus.DECLINED;
        this.resolvedBy = hrUserId;
    }

    public UUID getInterviewId() {
        return interviewId;
    }

    public RescheduleReason getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public List<LocalDate> getAvailableDays() {
        return availableDays == null ? List.of() : availableDays;
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public RescheduleStatus getStatus() {
        return status;
    }

    public Instant getNewStartAt() {
        return newStartAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }
}
