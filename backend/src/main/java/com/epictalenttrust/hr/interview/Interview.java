package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.common.AuditedEntity;
import com.epictalenttrust.hr.job.InterviewMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interviews")
public class Interview extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(nullable = false)
    private int round;

    private InterviewMode mode;

    @Column(name = "start_at")
    private Instant startAt;

    /** IANA zone, e.g. Asia/Kolkata. */
    @Column(name = "candidate_tz")
    private String candidateTz;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "time_source")
    private TimeSource timeSource;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "arrive_by")
    private Instant arriveBy;

    @Column(name = "meeting_link")
    private String meetingLink;

    private String venue;

    @Column(name = "panel_code_hash")
    private String panelCodeHash;

    @Column(name = "panel_code_enc")
    private String panelCodeEnc;

    @Column(name = "reply_by")
    private Instant replyBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(nullable = false)
    private InterviewStatus status = InterviewStatus.DRAFT;

    @Column(name = "scheduled_by")
    private UUID scheduledBy;

    @Column(name = "reminder_sent_at")
    private Instant reminderSentAt;

    protected Interview() {
    }

    @Column(name = "assessor_id")
    private UUID assessorId;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Interview(UUID applicationId, int round, InterviewMode mode, Integer durationMin) {
        this.applicationId = applicationId;
        this.round = round;
        this.mode = mode;
        this.durationMin = durationMin;
    }

    public void setTime(Instant startAt, String candidateTz, Instant replyBy, UUID scheduledBy) {
        this.startAt = startAt;
        this.candidateTz = candidateTz;
        this.replyBy = replyBy;
        this.scheduledBy = scheduledBy;
        this.timeSource = TimeSource.HR_SET;
    }

    public void markInvited(Instant at) {
        this.status = InterviewStatus.INVITED;
        this.invitedAt = at;
        this.reminderSentAt = null;
    }

    public void markConfirmed() {
        this.status = InterviewStatus.CONFIRMED;
    }

    public void markRescheduleRequested() {
        this.status = InterviewStatus.RESCHEDULE_REQUESTED;
    }

    public void markCancelled() {
        this.status = InterviewStatus.CANCELLED;
    }

    public void markCompleted() {
        this.status = InterviewStatus.COMPLETED;
    }

    public void markCompleted(Instant at) {
        this.status = InterviewStatus.COMPLETED;
        this.completedAt = at;
    }

    public void markNoShow(Instant at) {
        this.status = InterviewStatus.NO_SHOW;
        this.completedAt = at;
    }

    /** Round 2: a seat at a partner centre, with the assessor who will interview. */
    public void atCentre(UUID slotId, Instant arriveBy, UUID assessorId) {
        this.slotId = slotId;
        this.arriveBy = arriveBy;
        this.assessorId = assessorId;
        this.timeSource = TimeSource.CENTRE_SLOT;
    }

    /** Round 3: the video-call link for the candidate and the panel. */
    /** A panel interview in person: where it happens. */
    public void setVenue(String venue) {
        this.venue = venue;
    }

    /** The code panel members enter with their employee ID: kept hashed for look-up and encrypted for HR to see. */
    public void setPanelCode(String hash, String encrypted) {
        this.panelCodeHash = hash;
        this.panelCodeEnc = encrypted;
    }

    public String getVenue() {
        return venue;
    }

    public String getPanelCodeEnc() {
        return panelCodeEnc;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public UUID getSlotId() {
        return slotId;
    }

    public Instant getArriveBy() {
        return arriveBy;
    }

    public UUID getAssessorId() {
        return assessorId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void restoreAfterKeptTime() {
        this.status = InterviewStatus.INVITED;
    }

    public void markReminderSent(Instant at) {
        this.reminderSentAt = at;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public int getRound() {
        return round;
    }

    public InterviewMode getMode() {
        return mode;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public String getCandidateTz() {
        return candidateTz;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public TimeSource getTimeSource() {
        return timeSource;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public Instant getReplyBy() {
        return replyBy;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public InterviewStatus getStatus() {
        return status;
    }

    public UUID getScheduledBy() {
        return scheduledBy;
    }

    public Instant getReminderSentAt() {
        return reminderSentAt;
    }
}
