package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.interview.InterviewDtos.CancelInput;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.interview.InterviewDtos.RescheduleDetail;
import com.epictalenttrust.hr.interview.InterviewDtos.RescheduleInput;
import com.epictalenttrust.hr.interview.InterviewDtos.RespondAction;
import com.epictalenttrust.hr.interview.InterviewDtos.RespondInput;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InterviewService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final Tenancy tenancy;
    private final InterviewRepository interviews;
    private final RescheduleRequestRepository requests;
    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final HrUserRepository users;
    private final InterviewQueries queries;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final InterviewFacts facts;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final com.epictalenttrust.hr.routing.CentreOptionsService centreOptions;
    private final com.epictalenttrust.hr.candidateportal.CandidateLinks candidateLinks;
    private final com.epictalenttrust.hr.mail.Outbox outbox;
    private final tools.jackson.databind.ObjectMapper json;
    private final JdbcClient jdbc;
    private final Clock clock;

    public InterviewService(Tenancy tenancy, InterviewRepository interviews, RescheduleRequestRepository requests,
                            JobApplicationRepository applications, JobRepository jobs, CompanyRepository companies,
                            HrUserRepository users, InterviewQueries queries, MessageService messages,
                            MessageTemplates templates, InterviewFacts facts, ActivityService activity,
                            NotificationService notifications,
                            com.epictalenttrust.hr.routing.CentreOptionsService centreOptions,
                            com.epictalenttrust.hr.candidateportal.CandidateLinks candidateLinks,
                            com.epictalenttrust.hr.mail.Outbox outbox, tools.jackson.databind.ObjectMapper json,
                            JdbcClient jdbc, Clock clock) {
        this.candidateLinks = candidateLinks;
        this.outbox = outbox;
        this.json = json;
        this.tenancy = tenancy;
        this.interviews = interviews;
        this.requests = requests;
        this.applications = applications;
        this.jobs = jobs;
        this.companies = companies;
        this.users = users;
        this.queries = queries;
        this.messages = messages;
        this.templates = templates;
        this.facts = facts;
        this.activity = activity;
        this.notifications = notifications;
        this.centreOptions = centreOptions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InterviewRow> list(InterviewQueries.View view, UUID jobId) {
        if (jobId != null) {
            tenancy.job(jobId);
        }
        return queries.list(view, jobId);
    }

    /** The candidate replied (by email) to accept the time. */
    @Transactional
    public InterviewRow accept(UUID interviewId) {
        Interview interview = tenancy.interview(interviewId);
        if (interview.getStatus() != InterviewStatus.INVITED) {
            throw ApiException.conflict("not_invited", "Only an invitation that's waiting for a reply can be accepted.");
        }
        interview.markConfirmed();
        alert(interview, NotificationKind.INTERVIEW_ACCEPTED, "%s accepted %s",
                "The interview is confirmed for %s.".formatted(when(interview)), null);
        activity.record("interview.accepted", "interview", interviewId);
        interviews.flush();
        return queries.byId(interviewId).orElseThrow();
    }

    @Transactional
    public InterviewRow cancel(UUID interviewId, CancelInput input) {
        Interview interview = tenancy.interview(interviewId);
        if (!interview.getStatus().isUpcoming() && interview.getStatus() != InterviewStatus.DRAFT) {
            throw ApiException.conflict("not_upcoming", "This interview can't be cancelled.");
        }
        boolean candidateKnew = interview.getStatus() != InterviewStatus.DRAFT;
        interview.markCancelled();
        releaseSeat(interview.getSlotId());
        tellPanel(interview, true);
        tellAssessor(interview);
        requests.findFirstByInterviewIdAndStatus(interviewId, RescheduleStatus.PENDING)
                .ifPresent(r -> r.decline(CurrentUser.id()));
        if (candidateKnew) {
            JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
            messages.emailCandidate(application, interviewId, SenderKind.HR_USER, CurrentUser.id(),
                    templates.cancelled(facts.of(interview, application), input == null ? null : input.reason()));
        }
        activity.record("interview.cancelled", "interview", interviewId);
        interviews.flush();
        return queries.byId(interviewId).orElseThrow();
    }

    /**
     * The job closed: the interview is cancelled, its seat released, the panel or assessor told, and the candidate
     * gets the "position closed" cancellation (an interview that was never sent is just removed).
     *
     * @return true if the candidate was emailed
     */
    @Transactional
    public boolean cancelBecauseJobClosed(Interview interview, String reason) {
        boolean candidateKnew = interview.getStatus() != InterviewStatus.DRAFT;
        interview.markCancelled();
        jdbc.sql("update interviews set cancel_reason = :r where id = :id")
                .param("r", "The job closed" + (reason == null || reason.isBlank() ? "" : ": " + reason.strip()))
                .param("id", interview.getId()).update();
        releaseSeat(interview.getSlotId());
        if (candidateKnew) {
            tellPanel(interview, true);
            tellAssessor(interview);
        }
        requests.findFirstByInterviewIdAndStatus(interview.getId(), RescheduleStatus.PENDING)
                .ifPresent(r -> r.decline(CurrentUser.id()));
        if (candidateKnew) {
            JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
            messages.emailCandidate(application, interview.getId(), SenderKind.SYSTEM, null,
                    templates.jobClosedInterview(facts.of(interview, application)));
        }
        activity.record("interview.cancelled_job_closed", "interview", interview.getId());
        return candidateKnew;
    }

    /** The candidate didn't attend. HR can then schedule the round again or decide not to progress. */
    @Transactional
    public InterviewRow markNoShow(UUID interviewId) {
        Interview interview = tenancy.interview(interviewId);
        if (interview.getStatus() != InterviewStatus.INVITED && interview.getStatus() != InterviewStatus.CONFIRMED) {
            throw ApiException.conflict("not_upcoming", "Only a scheduled interview can be marked as not attended.");
        }
        if (interview.getStartAt() == null || interview.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("too_early", "You can mark this after the interview's start time.");
        }
        interview.markNoShow(clock.instant());
        releaseSeat(interview.getSlotId());
        activity.record("interview.no_show", "interview", interviewId);
        interviews.flush();
        return queries.byId(interviewId).orElseThrow();
    }

    /** HR records a candidate's request for a different time (they replied to the invitation by email). */
    @Transactional
    public RescheduleDetail logRequest(UUID interviewId, RescheduleInput input) {
        Interview interview = tenancy.interview(interviewId);
        if (interview.getStatus() != InterviewStatus.INVITED && interview.getStatus() != InterviewStatus.CONFIRMED) {
            throw ApiException.conflict("not_invited", "A different time can only be requested for an upcoming interview.");
        }
        if (requests.findFirstByInterviewIdAndStatus(interviewId, RescheduleStatus.PENDING).isPresent()) {
            throw ApiException.conflict("request_open", "There's already an open request for this interview.");
        }
        RescheduleRequest request = requests.save(new RescheduleRequest(interviewId, input.reason(),
                blankToNull(input.note()), input.availableDays().stream().sorted().distinct().toList(), input.timeOfDay()));
        interview.markRescheduleRequested();
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        String days = request.getAvailableDays().stream().map(DAY::format).collect(Collectors.joining(", "));
        messages.recordFromCandidate(application, interviewId, interview.getScheduledBy(),
                "Reschedule request · " + roundLabel(interview),
                "Reason: " + label(input.reason()) + (request.getNote() == null ? "" : "\n\n" + request.getNote())
                        + "\n\nAvailable: " + days + (input.timeOfDay() == null ? "" : " · " + label(input.timeOfDay())));
        alert(interview, NotificationKind.RESCHEDULE_REQUESTED, "%s asked to move %s",
                "Reason: " + label(input.reason()) + ". Available: " + days + ". Approve and send a new time, or keep the original.",
                request.getId());
        activity.record("reschedule.requested", "interview", interviewId);
        return detail(request.getId(), null);
    }

    @Transactional(readOnly = true)
    public RescheduleDetail detail(UUID requestId, String hrZoneParam) {
        RescheduleRequest request = tenancy.rescheduleRequest(requestId);
        Interview interview = interviews.findById(request.getInterviewId()).orElseThrow();
        InterviewRow row = queries.byId(interview.getId()).orElseThrow();
        List<Instant> suggestions = List.of();
        if (request.getStatus() == RescheduleStatus.PENDING) {
            ZoneId candidateZone = TimeZones.parseOr(interview.getCandidateTz(), TimeZones.INDIA);
            ZoneId hrZone = TimeZones.parseOr(hrZoneParam, hrZoneFor(interview));
            Duration length = Duration.ofMinutes(interview.getDurationMin() == null ? 45 : interview.getDurationMin());
            Instant now = clock.instant();
            List<SlotSuggester.Interval> busy = interviews.findBusy(CurrentUser.id(), now, now.plus(Duration.ofDays(45))).stream()
                    .filter(i -> !i.getId().equals(interview.getId()) && i.getStartAt() != null)
                    .map(i -> new SlotSuggester.Interval(i.getStartAt(),
                            i.getStartAt().plus(Duration.ofMinutes(i.getDurationMin() == null ? 45 : i.getDurationMin()))))
                    .toList();
            suggestions = SlotSuggester.onDays(candidateZone, request.getAvailableDays(), request.getTimeOfDay(), hrZone,
                    now, length, busy, 6);
        }
        String resolvedBy = request.getResolvedBy() == null ? null
                : users.findById(request.getResolvedBy()).map(u -> u.getFullName()).orElse(null);
        record Extra(String availability, boolean byCandidate) {
        }
        Extra extra = jdbc.sql("select availability::text as a, requested_by_candidate from reschedule_requests where id = :id")
                .param("id", requestId)
                .query((rs, n) -> new Extra(rs.getString("a"), rs.getBoolean("requested_by_candidate"))).single();
        List<InterviewDtos.Availability> availability = extra.availability() == null ? List.of()
                : json.readValue(extra.availability(), new tools.jackson.core.type.TypeReference<List<InterviewDtos.Availability>>() {
                });
        return new RescheduleDetail(request.getId(), request.getStatus(), request.getReason(), request.getNote(),
                request.getAvailableDays(), request.getTimeOfDay(), request.getCreatedAt(), request.getNewStartAt(),
                resolvedBy, row, suggestions, availability, extra.byCandidate());
    }

    /** "Technical Level 1 (Round 2)". */
    private String roundLabel(Interview interview) {
        String name = jdbc.sql("""
                        select jr.name from job_rounds jr join applications a on a.job_id = jr.job_id
                         where a.id = :a and jr.round = :r
                        """)
                .param("a", interview.getApplicationId()).param("r", interview.getRound())
                .query(String.class).optional().orElse(null);
        return MessageTemplates.label(name, interview.getRound());
    }

    @Transactional
    public RescheduleDetail respond(UUID requestId, RespondInput input) {
        RescheduleRequest request = tenancy.rescheduleRequest(requestId);
        if (request.getStatus() != RescheduleStatus.PENDING) {
            throw ApiException.conflict("already_answered", "This request has already been answered.");
        }
        Interview interview = interviews.findById(request.getInterviewId()).orElseThrow();
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        UUID hrUserId = CurrentUser.id();
        Instant now = clock.instant();
        if (input.action() == RespondAction.NEW_TIME) {
            String note = input.message();
            Instant newStart;
            if (interview.getSlotId() != null) {
                String centreNote = input.newSlotId() == null ? null : centreOptions.checkCentreChoice(
                        application.getCandidateId(), centreOf(input.newSlotId()), input.outsideReason());
                NewSeat seat = moveSeat(interview, input.newSlotId(), input.assessorId());
                if (centreNote != null) {
                    jdbc.sql("update interviews set centre_note = :n where id = :id").param("n", centreNote)
                            .param("id", interview.getId()).update();
                }
                newStart = seat.start();
                note = seat.directions() + (note == null || note.isBlank() ? "" : "\n\n" + note.strip());
            } else {
                newStart = input.newStartAt();
            }
            if (newStart == null || !newStart.isAfter(now.plus(Duration.ofHours(2)))) {
                throw ApiException.badRequest("time_past", "Choose a new time at least two hours from now.");
            }
            Instant replyBy = later(now.plus(Duration.ofHours(24)), null);
            if (replyBy.isAfter(newStart.minus(Duration.ofHours(2)))) {
                replyBy = newStart.minus(Duration.ofHours(2));
            }
            interview.setTime(newStart, interview.getCandidateTz(), replyBy, hrUserId);
            if (interview.getSlotId() != null) {
                interview.atCentre(input.newSlotId(), newStart.minus(Duration.ofMinutes(15)),
                        input.assessorId() != null ? input.assessorId() : interview.getAssessorId());
                request.approveWithSlot(newStart, input.newSlotId(), hrUserId);
            } else {
                request.approve(newStart, hrUserId);
            }
            interview.markInvited(now);
            tellPanel(interview, false);
            messages.emailCandidate(application, interview.getId(), SenderKind.HR_USER, hrUserId,
                    templates.newTime(facts.of(interview, application)
                            .withCandidatePage(candidateLinks.issue(application.getId(), interview)), note));
            activity.record("reschedule.new_time_sent", "interview", interview.getId());
        } else {
            request.decline(hrUserId);
            interview.restoreAfterKeptTime();
            messages.emailCandidate(application, interview.getId(), SenderKind.HR_USER, hrUserId,
                    templates.keptTime(facts.of(interview, application), input.message()));
            activity.record("reschedule.kept_original", "interview", interview.getId());
        }
        return detail(requestId, null);
    }

    private record NewSeat(Instant start, String directions) {
    }

    /** Round 2: books a seat in the new slot and frees the old one. */
    private UUID centreOf(UUID slotId) {
        return jdbc.sql("select centre_id from centre_slots where id = :id").param("id", slotId).query(UUID.class)
                .optional().orElseThrow(() -> ApiException.notFound("Slot"));
    }

    private NewSeat moveSeat(Interview interview, UUID slotId, UUID assessorId) {
        if (slotId == null) {
            throw ApiException.badRequest("slot_required", "Choose a new slot at a partner assessment centre.");
        }
        record Slot(UUID centreId, Instant start, Instant end, String name, String address, String zone) {
        }
        Slot slot = jdbc.sql("""
                        select s.centre_id, s.start_at, s.end_at, c.name, concat_ws(', ', c.address, c.city) as address,
                               c.time_zone
                          from centre_slots s join partner_centres c on c.id = s.centre_id where s.id = :id
                        """)
                .param("id", slotId)
                .query((rs, row) -> new Slot(com.epictalenttrust.hr.common.Rows.uuid(rs, "centre_id"),
                        com.epictalenttrust.hr.common.Rows.instant(rs, "start_at"),
                        com.epictalenttrust.hr.common.Rows.instant(rs, "end_at"), rs.getString("name"),
                        rs.getString("address"), rs.getString("time_zone")))
                .optional().orElseThrow(() -> ApiException.notFound("Slot"));
        if (assessorId != null) {
            boolean atCentre = jdbc.sql("select exists (select 1 from partner_assessors where id = :a and centre_id = :c and status = 'active')")
                    .param("a", assessorId).param("c", slot.centreId()).query(Boolean.class).single();
            if (!atCentre) {
                throw ApiException.badRequest("wrong_assessor", "Choose an assessor who works at this centre.");
            }
        }
        UUID chosenAssessor = assessorId != null ? assessorId : interview.getAssessorId();
        if (chosenAssessor != null && !centreOptions.assessorFree(chosenAssessor, slot.start(), slot.end(), interview.getId())) {
            throw ApiException.conflict("assessor_busy", "That assessor already has an interview at this time. Choose another assessor or slot.");
        }
        if (!slotId.equals(interview.getSlotId())) {
            int booked = jdbc.sql("update centre_slots set booked = booked + 1 where id = :id and booked < seats")
                    .param("id", slotId).update();
            if (booked == 0) {
                throw ApiException.conflict("slot_full", "That slot has just filled up. Choose another one.");
            }
            UUID oldCentre = interview.getSlotId() == null ? null
                    : jdbc.sql("select centre_id from centre_slots where id = :id").param("id", interview.getSlotId())
                            .query(UUID.class).optional().orElse(null);
            releaseSeat(interview.getSlotId());
            if (!slot.centreId().equals(oldCentre)) {
                centreOptions.recordTurn(slot.centreId());
            }
        }
        ZoneId zone = TimeZones.parseOr(slot.zone(), TimeZones.parseOr(interview.getCandidateTz(), TimeZones.INDIA));
        String arrive = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                .format(slot.start().minus(Duration.ofMinutes(15)).atZone(zone));
        return new NewSeat(slot.start(), "Where: " + slot.name() + ", " + slot.address()
                + "\nPlease arrive by " + arrive + " and bring your Aadhaar and PAN cards.");
    }

    private void releaseSeat(UUID slotId) {
        if (slotId != null) {
            jdbc.sql("update centre_slots set booked = greatest(booked - 1, 0) where id = :id").param("id", slotId).update();
        }
    }

    /** Tells HR (whoever scheduled it and the job owner) about something that needs their attention. */
    private void alert(Interview interview, NotificationKind kind, String titleFormat, String body, UUID requestId) {
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        String name = candidateName(application);
        String link = "/applications/" + application.getId() + (requestId == null ? "" : "?reschedule=" + requestId);
        notifications.notify(new NotificationService.Request(job.getCompanyId(),
                java.util.Arrays.asList(interview.getScheduledBy(), notifications.jobOwner(job.getId())), kind,
                titleFormat.formatted(name, roundLabel(interview)), body + " · " + job.getTitle(), link, job.getId(),
                application.getId(), interview.getId()));
    }

    /** Emails each panel member that the interview moved (their code stays the same) or was cancelled. */
    private void tellPanel(Interview interview, boolean cancelled) {
        List<InterviewDtos.PanelMemberRef> members = queries.panel(interview.getId());
        if (members.isEmpty()) {
            return;
        }
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        var f = facts.of(interview, application);
        java.time.ZoneId zone = com.epictalenttrust.hr.common.TimeZones.parseOr(jdbc.sql("select time_zone from companies where id = :c")
                .param("c", job.getCompanyId()).query(String.class).optional().orElse(null),
                com.epictalenttrust.hr.common.TimeZones.INDIA);
        for (InterviewDtos.PanelMemberRef member : members) {
            var email = templates.panelUpdate(f, member.fullName(), candidateName(application), cancelled, zone);
            outbox.queue(new com.epictalenttrust.hr.mail.Outbox.Email(job.getCompanyId(), job.getId(), interview.getId(),
                    cancelled ? "panel_cancelled" : "panel_moved", member.email(), member.fullName(), email.subject(),
                    email.body()));
        }
    }

    /** Emails the partner assessor that the assessment is cancelled. */
    private void tellAssessor(Interview interview) {
        if (interview.getAssessorId() == null) {
            return;
        }
        record Assessor(String name, String email) {
        }
        Assessor assessor = jdbc.sql("select full_name, email from partner_assessors where id = :id")
                .param("id", interview.getAssessorId())
                .query((rs, n) -> new Assessor(rs.getString("full_name"), rs.getString("email"))).optional().orElse(null);
        if (assessor == null || assessor.email() == null) {
            return;
        }
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        String first = assessor.name() == null ? "there" : assessor.name().strip().split("\\s+")[0];
        String company = companies.findById(job.getCompanyId()).map(c -> c.getName()).orElse("the hiring company");
        String body = """
                Hello %s,

                The assessment with %s for the %s role on %s has been cancelled. You don't need to do anything, and the report link no longer works.

                Thank you,
                %s""".formatted(first, candidateName(application), job.getTitle(), when(interview), company);
        outbox.queue(new com.epictalenttrust.hr.mail.Outbox.Email(job.getCompanyId(), job.getId(), interview.getId(),
                "assessor_cancelled", assessor.email(), assessor.name(),
                "Cancelled: assessment with " + candidateName(application), body));
        jdbc.sql("update assessor_links set expires_at = now() where interview_id = :i and used_at is null")
                .param("i", interview.getId()).update();
    }

    private String candidateName(JobApplication application) {
        return jdbc.sql("select full_name from candidates where id = :c").param("c", application.getCandidateId())
                .query(String.class).optional().orElse("A candidate");
    }

    private String when(Interview interview) {
        if (interview.getStartAt() == null) {
            return "a time to be agreed";
        }
        return DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)
                .format(interview.getStartAt().atZone(TimeZones.INDIA)) + " IST";
    }

    private ZoneId hrZoneFor(Interview interview) {
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        String country = companies.findById(job.getCompanyId()).map(c -> c.getCountry()).orElse(null);
        return TimeZones.forCountry(country);
    }

    private static Instant later(Instant a, Instant b) {
        return b == null || a.isAfter(b) ? a : b;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static String label(Enum<?> value) {
        return switch (value.name()) {
            case "CLASH" -> "Clash with another commitment";
            case "ILLNESS" -> "Illness";
            case "TRAVEL" -> "Travel";
            case "OTHER" -> "Something else";
            case "MORNING" -> "mornings";
            case "AFTERNOON" -> "afternoons";
            case "EVENING" -> "evenings";
            default -> "any time";
        };
    }
}
