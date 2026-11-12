package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.job.InterviewMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InterviewDtos {

    private InterviewDtos() {
    }

    public record PlanItem(UUID applicationId, String candidateName, String city, String country, String candidateZone,
                           Integer matchScore, UUID interviewId, InterviewStatus status, Instant startAt,
                           Instant replyBy, boolean suggested) {
    }

    /** Booking several candidates' AI interviews for one round of a job at once. */
    public record Round1Plan(UUID jobId, String jobTitle, String jobRef, int round, String roundName, int durationMin,
                             InterviewMode mode, String hrZone, Instant defaultReplyBy, List<PlanItem> items) {
    }

    public record TimeInput(@NotNull UUID applicationId, @NotNull Instant startAt, @NotBlank String candidateZone) {
    }

    public record Round1Request(@NotNull Instant replyBy, @NotEmpty @Size(max = 200) List<@Valid TimeInput> items) {
    }

    public record SaveResult(int saved, int sent) {
    }

    public record InterviewRow(UUID id, UUID applicationId, UUID jobId, String jobTitle, String candidateName,
                               String candidateCountry, int round, InterviewMode mode, Instant startAt,
                               String candidateTz, Integer durationMin, InterviewStatus status, Instant replyBy,
                               Instant invitedAt, UUID pendingRequestId, String scheduledByName, String meetingLink,
                               Instant arriveBy, String centreName, String centreAddress, String assessorName,
                               int panelSize, int feedbackCount, Instant completedAt, String roundName,
                               com.epictalenttrust.hr.job.RoundKind roundKind, String venue, String panelCode,
                               List<PanelMemberRef> panel, com.epictalenttrust.hr.job.JobDtos.Changes changes,
                               String centreNote, Instant panelCodeFrom, Instant panelCodeUntil) {

        public InterviewRow withExtras(String code, List<PanelMemberRef> members,
                                       com.epictalenttrust.hr.job.JobDtos.Changes changeInfo) {
            return new InterviewRow(id, applicationId, jobId, jobTitle, candidateName, candidateCountry, round, mode,
                    startAt, candidateTz, durationMin, status, replyBy, invitedAt, pendingRequestId, scheduledByName,
                    meetingLink, arriveBy, centreName, centreAddress, assessorName, panelSize, feedbackCount,
                    completedAt, roundName, roundKind, venue, code, members, changeInfo, centreNote, panelCodeFrom,
                    panelCodeUntil);
        }
    }

    /** Someone on an interview's panel, as HR entered them, and whether their review is in. */
    public record PanelMemberRef(UUID id, String employeeId, String fullName, String email, boolean reviewSubmitted,
                                 boolean reviewStarted) {
    }

    public record RescheduleInput(@NotNull RescheduleReason reason, @Size(max = 500) String note,
                                  @NotEmpty @Size(max = 10) List<LocalDate> availableDays, TimeOfDay timeOfDay) {
    }

    /**
     * One date a candidate says they're free: morning, afternoon, any time, or a from–to time (24-hour "HH:mm",
     * in the candidate's own time zone).
     */
    public record Availability(@NotNull LocalDate date, @NotBlank String window, String from, String to) {
    }

    public record RescheduleDetail(UUID id, RescheduleStatus status, RescheduleReason reason, String note,
                                   List<LocalDate> availableDays, TimeOfDay timeOfDay, Instant requestedAt,
                                   Instant newStartAt, String resolvedByName, InterviewRow interview,
                                   List<Instant> suggestions, List<Availability> availability,
                                   boolean requestedByCandidate) {
    }

    public enum RespondAction { NEW_TIME, KEEP_ORIGINAL }

    /** For Round 2 the new time is a partner-centre slot (newSlotId); for Rounds 1 and 3 it's newStartAt. */
    public record RespondInput(@NotNull RespondAction action, Instant newStartAt, UUID newSlotId, UUID assessorId,
                               @Size(max = 1000) String message, @Size(max = 500) String outsideReason) {
    }

    public record CancelInput(@Size(max = 500) String reason) {
    }
}
