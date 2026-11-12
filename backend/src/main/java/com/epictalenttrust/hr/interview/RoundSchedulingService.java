package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.PipelineQueries;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.candidateportal.CandidateLinks;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRound;
import com.epictalenttrust.hr.job.JobRoundRepository;
import com.epictalenttrust.hr.job.JobStatus;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.mail.Outbox;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.routing.CentreOptionsService;
import com.epictalenttrust.hr.security.Access;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.security.SecretCipher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Books one candidate's current round, from their page, the Interviews page or the job. Each job sets its own
 * rounds; the type of the round decides how it is booked: an AI interview at a time HR sets, a partner assessor at a
 * centre slot, or a panel interview with the panel members HR types in (employee ID, name, email). A round opens only
 * when the candidate has passed the one before (or straight away for Round 1 when AI shortlisting is off).
 */
@Service
public class RoundSchedulingService {

    public record AiInput(@NotNull Instant startAt, @NotBlank String candidateZone,
                          @Min(15) @Max(480) Integer durationMin, Instant replyBy) {
    }

    /** @param outsideReason why HR chose a centre outside the candidate's nearest centre city (needed only then) */
    public record PartnerInput(@NotNull UUID slotId, UUID assessorId, Instant replyBy,
                               @jakarta.validation.constraints.Size(max = 500) String outsideReason) {
    }

    public record MemberInput(@NotBlank @Size(max = 40) String employeeId, @NotBlank @Size(max = 120) String fullName,
                              @NotBlank @Email @Size(max = 200) String email) {
    }

    public record PanelInput(@NotNull Instant startAt, @NotBlank String candidateZone,
                             @Min(15) @Max(480) Integer durationMin, @NotNull InterviewMode mode,
                             @Size(max = 500) String meetingLink, @Size(max = 300) String venue,
                             @NotEmpty @Size(max = 12) List<@Valid MemberInput> panel, Instant replyBy) {
    }

    /** The round a candidate is on: its number and the job's plan for it. */
    public record CurrentRound(int number, String name, RoundKind kind, InterviewMode mode, int durationMin,
                               Integer targetDays) {
    }

    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final Tenancy tenancy;
    private final InterviewRepository interviews;
    private final JobRoundRepository rounds;
    private final CandidateRepository candidates;
    private final InterviewQueries queries;
    private final InterviewFacts facts;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final AssessorLinkService assessorLinks;
    private final CentreOptionsService centreOptions;
    private final CandidateLinks candidateLinks;
    private final Outbox outbox;
    private final SecretCipher cipher;
    private final Access access;
    private final ActivityService activity;
    private final EttProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RoundSchedulingService(Tenancy tenancy, InterviewRepository interviews, JobRoundRepository rounds,
                                  CandidateRepository candidates, InterviewQueries queries, InterviewFacts facts,
                                  MessageService messages, MessageTemplates templates, AssessorLinkService assessorLinks,
                                  CentreOptionsService centreOptions, CandidateLinks candidateLinks, Outbox outbox,
                                  SecretCipher cipher, Access access, ActivityService activity,
                                  EttProperties properties, JdbcClient jdbc, Clock clock) {
        this.tenancy = tenancy;
        this.interviews = interviews;
        this.rounds = rounds;
        this.candidates = candidates;
        this.queries = queries;
        this.facts = facts;
        this.messages = messages;
        this.templates = templates;
        this.assessorLinks = assessorLinks;
        this.centreOptions = centreOptions;
        this.candidateLinks = candidateLinks;
        this.outbox = outbox;
        this.cipher = cipher;
        this.access = access;
        this.activity = activity;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** The candidate's current round, as the job's plan describes it. */
    @Transactional(readOnly = true)
    public CurrentRound currentRound(UUID applicationId) {
        JobApplication application = tenancy.application(applicationId);
        return current(application);
    }

    // ------------------------------------------------------------------ AI interview, online

    @Transactional
    public InterviewRow scheduleAi(UUID applicationId, AiInput input) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        CurrentRound round = open(application, job, RoundKind.AI_INTERVIEW);
        Instant now = clock.instant();
        requireFuture(input.startAt(), now);
        int minutes = input.durationMin() == null ? round.durationMin() : input.durationMin();
        ZoneId zone = TimeZones.parseOr(input.candidateZone(), TimeZones.INDIA);
        Interview interview = interviews.findByApplicationIdAndRoundOrderByCreatedAtDesc(applicationId, round.number())
                .stream().filter(i -> i.getStatus() == InterviewStatus.DRAFT).findFirst()
                .orElseGet(() -> new Interview(applicationId, round.number(), InterviewMode.ONLINE, minutes));
        interview.setTime(input.startAt(), zone.getId(), replyBy(input.replyBy(), input.startAt(), now), CurrentUser.id());
        interview = interviews.save(interview);
        interview.markInvited(now);
        enterRound(application, round);
        interviews.flush();
        var f = facts.of(interview, application).withCandidatePage(candidateLinks.issue(applicationId, interview));
        invite(application, interview, templates.invitation(f));
        activity.record("interview.invited", "interview", interview.getId());
        return queries.byId(interview.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------ Partner assessor, in person at a centre

    @Transactional
    public InterviewRow schedulePartner(UUID applicationId, PartnerInput input) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        CurrentRound round = open(application, job, RoundKind.PARTNER);
        record Slot(UUID centreId, Instant start, Instant end, String name, String address) {
        }
        Slot slot = jdbc.sql("""
                        select s.centre_id, s.start_at, s.end_at, c.name, concat_ws(', ', c.address, c.city) as address
                          from centre_slots s join partner_centres c on c.id = s.centre_id where s.id = :id
                        """)
                .param("id", input.slotId())
                .query((rs, row) -> new Slot(Rows.uuid(rs, "centre_id"), Rows.instant(rs, "start_at"),
                        Rows.instant(rs, "end_at"), rs.getString("name"), rs.getString("address")))
                .optional().orElseThrow(() -> ApiException.notFound("Slot"));
        Instant now = clock.instant();
        requireFuture(slot.start(), now);
        String centreNote = centreOptions.checkCentreChoice(application.getCandidateId(), slot.centreId(), input.outsideReason());
        UUID assessorId = input.assessorId() != null ? input.assessorId() : jdbc.sql(
                        "select id from partner_assessors where centre_id = :c and status = 'active' order by full_name")
                .param("c", slot.centreId()).query(UUID.class).list().stream()
                .filter(a -> centreOptions.assessorFree(a, slot.start(), slot.end(), null))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("no_assessor", "No assessor at this centre is free at that time."));
        boolean assessorAtCentre = jdbc.sql("select exists (select 1 from partner_assessors where id = :a and centre_id = :c and status = 'active')")
                .param("a", assessorId).param("c", slot.centreId()).query(Boolean.class).single();
        if (!assessorAtCentre) {
            throw ApiException.badRequest("wrong_assessor", "Choose an assessor who works at this centre.");
        }
        if (!centreOptions.assessorFree(assessorId, slot.start(), slot.end(), null)) {
            throw ApiException.conflict("assessor_busy", "That assessor already has an interview at this time. Choose another assessor or slot.");
        }
        int booked = jdbc.sql("update centre_slots set booked = booked + 1 where id = :id and booked < seats")
                .param("id", input.slotId()).update();
        if (booked == 0) {
            throw ApiException.conflict("slot_full", "That slot has just filled up. Choose another one.");
        }
        centreOptions.recordTurn(slot.centreId());
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        ZoneId zone = TimeZones.forCountry(candidate.getCountry());
        int minutes = (int) Math.max(15, Duration.between(slot.start(), slot.end()).toMinutes());
        Interview interview = new Interview(applicationId, round.number(), InterviewMode.IN_PERSON, minutes);
        interview.setTime(slot.start(), zone.getId(), replyBy(input.replyBy(), slot.start(), now), CurrentUser.id());
        interview.atCentre(input.slotId(), slot.start().minus(Duration.ofMinutes(15)), assessorId);
        interview = interviews.save(interview);
        interview.markInvited(now);
        enterRound(application, round);
        interviews.flush();
        if (centreNote != null) {
            jdbc.sql("update interviews set centre_note = :n where id = :id").param("n", centreNote)
                    .param("id", interview.getId()).update();
        }
        var f = facts.of(interview, application).withCandidatePage(candidateLinks.issue(applicationId, interview));
        invite(application, interview, templates.invitationAtCentre(f, slot.name(), slot.address(), interview.getArriveBy()));
        assessorLinks.issue(interview, job, candidate);
        activity.record("interview.invited", "interview", interview.getId());
        return queries.byId(interview.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------ Panel interview, online or in person

    @Transactional
    public InterviewRow schedulePanel(UUID applicationId, PanelInput input) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        CurrentRound round = open(application, job, RoundKind.PANEL);
        Instant now = clock.instant();
        requireFuture(input.startAt(), now);
        List<MemberInput> members = tidyPanel(input.panel());
        String link = null;
        String venue = null;
        if (input.mode() == InterviewMode.IN_PERSON) {
            venue = input.venue() == null ? "" : input.venue().strip();
            if (venue.length() < 3) {
                throw ApiException.badRequest("venue_required", "Add where the interview takes place (office address and room).");
            }
        } else {
            link = input.meetingLink() == null ? "" : input.meetingLink().strip();
            if (!link.matches("https://[^\\s]+")) {
                throw ApiException.badRequest("invalid_link", "Paste the full meeting link, starting with https://.");
            }
        }
        int minutes = input.durationMin() == null ? round.durationMin() : input.durationMin();
        ZoneId zone = TimeZones.parseOr(input.candidateZone(), TimeZones.INDIA);
        InterviewMode mode = input.mode() == InterviewMode.IN_PERSON ? InterviewMode.IN_PERSON : InterviewMode.ONLINE;
        Interview interview = new Interview(applicationId, round.number(), mode, minutes);
        interview.setTime(input.startAt(), zone.getId(), replyBy(input.replyBy(), input.startAt(), now), CurrentUser.id());
        interview.setMeetingLink(link);
        interview.setVenue(venue);
        String code = newCode();
        interview.setPanelCode(hashCode(code), cipher.encrypt(code));
        interview = interviews.save(interview);
        interview.markInvited(now);
        enterRound(application, round);
        interviews.flush();
        for (MemberInput member : members) {
            jdbc.sql("""
                    insert into panel_members (interview_id, employee_id, full_name, email, created_by)
                    values (:i, :e, :n, :m, :by)
                    """)
                    .param("i", interview.getId()).param("e", member.employeeId()).param("n", member.fullName())
                    .param("m", member.email()).param("by", CurrentUser.id()).update();
        }
        var f = facts.of(interview, application).withCandidatePage(candidateLinks.issue(applicationId, interview));
        invite(application, interview, templates.invitationWithPanel(f, link, venue));
        emailPanel(interview, job, application, members, code);
        activity.record("interview.invited", "interview", interview.getId());
        return queries.byId(interview.getId()).orElseThrow();
    }

    /**
     * HR changes who is on a panel (for example to fix an email address). New or changed members get the code by
     * email; members who already sent a review stay on the panel.
     */
    @Transactional
    public InterviewRow updatePanel(UUID interviewId, List<MemberInput> input) {
        Interview interview = tenancy.interview(interviewId);
        JobApplication application = tenancy.application(interview.getApplicationId());
        Job job = tenancy.job(application.getJobId());
        if (interview.getStatus() == InterviewStatus.CANCELLED || interview.getStatus() == InterviewStatus.COMPLETED) {
            throw ApiException.conflict("interview_closed", "This interview is finished or cancelled.");
        }
        String code = interview.getPanelCodeEnc() == null ? null : cipher.decrypt(interview.getPanelCodeEnc());
        if (code == null) {
            throw ApiException.badRequest("not_panel_round", "Only panel interviews have a panel.");
        }
        List<MemberInput> members = tidyPanel(input);
        List<InterviewDtos.PanelMemberRef> current = queries.panel(interviewId);
        Set<String> keep = new HashSet<>();
        members.forEach(m -> keep.add(m.employeeId().toUpperCase(Locale.ROOT)));
        for (InterviewDtos.PanelMemberRef existing : current) {
            if (!keep.contains(existing.employeeId().toUpperCase(Locale.ROOT))) {
                if (existing.reviewSubmitted()) {
                    throw ApiException.conflict("review_sent", existing.fullName()
                            + " has already sent a review, so they stay on the panel.");
                }
                jdbc.sql("delete from panel_members where id = :id").param("id", existing.id()).update();
            }
        }
        List<MemberInput> toEmail = new java.util.ArrayList<>();
        for (MemberInput member : members) {
            var match = current.stream()
                    .filter(c -> c.employeeId().equalsIgnoreCase(member.employeeId())).findFirst();
            if (match.isEmpty()) {
                jdbc.sql("insert into panel_members (interview_id, employee_id, full_name, email, created_by) values (:i, :e, :n, :m, :by)")
                        .param("i", interviewId).param("e", member.employeeId()).param("n", member.fullName())
                        .param("m", member.email()).param("by", CurrentUser.id()).update();
                toEmail.add(member);
            } else if (!match.get().email().equalsIgnoreCase(member.email()) || !match.get().fullName().equals(member.fullName())) {
                jdbc.sql("update panel_members set full_name = :n, email = :m where id = :id")
                        .param("n", member.fullName()).param("m", member.email()).param("id", match.get().id()).update();
                if (!match.get().email().equalsIgnoreCase(member.email())) {
                    toEmail.add(member);
                }
            }
        }
        emailPanel(interview, job, application, toEmail, code);
        activity.record("interview.panel_changed", "interview", interviewId);
        return queries.byId(interviewId).orElseThrow();
    }

    /** Sends each panel member their interview code, the time, and where to sign in to review. */
    void emailPanel(Interview interview, Job job, JobApplication application, List<MemberInput> members, String code) {
        if (members.isEmpty()) {
            return;
        }
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        var f = facts.of(interview, application);
        ZoneId zone = companyZone(job.getCompanyId());
        String where = interview.getMeetingLink() != null ? "Join here: " + interview.getMeetingLink()
                : "Where: " + interview.getVenue();
        String signIn = properties.portalUrl().replaceAll("/+$", "") + "/panel";
        for (MemberInput member : members) {
            MessageTemplates.Content email = templates.panelInvitation(f, member.fullName(), candidate.getFullName(), where,
                    code, signIn, zone);
            outbox.queue(new Outbox.Email(job.getCompanyId(), job.getId(), interview.getId(), "panel_invitation",
                    member.email(), member.fullName(), email.subject(), email.body()));
        }
        jdbc.sql("update panel_members set notified_at = now() where interview_id = :i")
                .param("i", interview.getId()).update();
    }

    // ------------------------------------------------------------------ helpers

    private CurrentRound current(JobApplication application) {
        int number = application.getStage() == ApplicationStage.SHORTLIST ? 1 : application.getStage().roundNumber();
        if (number == 0) {
            throw ApiException.conflict("round_not_open", "This candidate isn't in an interview round.");
        }
        JobRound plan = rounds.findByJobIdAndRound(application.getJobId(), number)
                .orElseThrow(() -> ApiException.conflict("no_round_plan",
                        "This job has no Round " + number + ". Add it to the job's interview plan first."));
        return new CurrentRound(number, plan.getName(), plan.getKind(), plan.getMode(),
                plan.getDurationMin() == null ? 60 : plan.getDurationMin(), plan.getTargetDays());
    }

    /** The candidate's next step is to book this round, the round is of this type, and the job is open. */
    private CurrentRound open(JobApplication application, Job job, RoundKind kind) {
        requireLive(job);
        String next = access.bind(jdbc.sql(PipelineQueries.PIPELINE + "select next_action from pipeline where application_id = :id"))
                .param("id", application.getId())
                .query(String.class).optional().orElse("none");
        if (!"schedule_round".equals(next)) {
            throw ApiException.conflict("round_not_open",
                    "This candidate's next round can't be booked yet: the previous round needs a decision first, or it's already booked.");
        }
        CurrentRound round = current(application);
        if (round.kind() != kind) {
            throw ApiException.conflict("wrong_round_type", round.name() + " (Round " + round.number() + ") is a "
                    + round.kind().label().toLowerCase(Locale.ROOT) + ", not a " + kind.label().toLowerCase(Locale.ROOT) + ".");
        }
        return round;
    }

    /**
     * Sends the invitation. Any email about the last decision that is still waiting goes first, so the candidate
     * reads things in order. If they missed this round's last interview, the invitation says so.
     */
    private void invite(JobApplication application, Interview interview, MessageTemplates.Content content) {
        messages.releasePending(application.getId());
        messages.emailCandidate(application, interview.getId(), SenderKind.HR_USER, CurrentUser.id(),
                withMissedNote(interview, content));
    }

    private MessageTemplates.Content withMissedNote(Interview interview, MessageTemplates.Content content) {
        Instant missed = jdbc.sql("""
                        select start_at, status from interviews
                         where application_id = :a and round = :r and id <> :id and status <> 'draft'
                         order by created_at desc limit 1
                        """)
                .param("a", interview.getApplicationId()).param("r", interview.getRound()).param("id", interview.getId())
                .query((rs, n) -> "no_show".equals(rs.getString("status")) ? Rows.instant(rs, "start_at") : null)
                .optional().orElse(null);
        if (missed == null) {
            return content;
        }
        ZoneId zone = TimeZones.parseOr(interview.getCandidateTz(), TimeZones.INDIA);
        String note = "We missed you at your interview on "
                + java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH).format(missed.atZone(zone))
                + ". If something got in the way, that's fine: here is a new time.";
        String body = content.body();
        int firstBreak = body.indexOf("\n\n");
        body = firstBreak < 0 ? note + "\n\n" + body
                : body.substring(0, firstBreak) + "\n\n" + note + body.substring(firstBreak);
        return new MessageTemplates.Content(content.subject(), body);
    }

    static void requireLive(Job job) {
        if (job.getStatus() == JobStatus.ON_HOLD) {
            throw ApiException.conflict("job_on_hold", "This job is on hold, so new interviews can't be booked. Open it again first.");
        }
        if (job.getStatus() == JobStatus.CLOSED || job.getStatus() == JobStatus.ARCHIVED) {
            throw ApiException.conflict("job_closed", "This job is closed.");
        }
    }

    /** Round 1 from the shortlist: the round's clock started at the shortlist decision, so it keeps running. */
    private static void enterRound(JobApplication application, CurrentRound round) {
        if (application.getStage() == ApplicationStage.SHORTLIST && round.number() == 1) {
            application.moveTo(ApplicationStage.ROUND1);
        }
    }

    private static List<MemberInput> tidyPanel(List<MemberInput> input) {
        if (input == null || input.isEmpty()) {
            throw ApiException.badRequest("panel_required", "Add at least one panel member.");
        }
        Set<String> ids = new HashSet<>();
        List<MemberInput> out = new java.util.ArrayList<>();
        for (MemberInput m : input) {
            String id = m.employeeId() == null ? "" : m.employeeId().strip();
            String name = m.fullName() == null ? "" : m.fullName().strip().replaceAll("\\s+", " ");
            String email = m.email() == null ? "" : m.email().strip().toLowerCase(Locale.ROOT);
            if (id.isEmpty() || name.isEmpty()) {
                throw ApiException.badRequest("panel_incomplete", "Each panel member needs an employee ID, a name and an email.");
            }
            if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                throw ApiException.badRequest("invalid_email", "Check the email address for " + name + ".");
            }
            if (!ids.add(id.toUpperCase(Locale.ROOT))) {
                throw ApiException.badRequest("duplicate_employee", "Employee ID " + id + " is on the panel twice.");
            }
            out.add(new MemberInput(id, name, email));
        }
        return out;
    }

    private String newCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i == 4) {
                    code.append('-');
                }
                code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
            }
            boolean taken = jdbc.sql("select exists (select 1 from interviews where panel_code_hash = :h)")
                    .param("h", hashCode(code.toString())).query(Boolean.class).single();
            if (!taken) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Couldn't make a unique interview code");
    }

    /** Codes are compared without spaces or dashes and in capitals, so "k7p4 qm2x" works too. */
    public static String hashCode(String code) {
        String normal = code == null ? "" : code.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ZoneId companyZone(UUID companyId) {
        String zone = jdbc.sql("select time_zone from companies where id = :id").param("id", companyId)
                .query(String.class).optional().orElse(null);
        return TimeZones.parseOr(zone, TimeZones.INDIA);
    }

    static void requireFuture(Instant start, Instant now) {
        if (!start.isAfter(now.plus(Duration.ofHours(1)))) {
            throw ApiException.badRequest("time_past", "Interview times must be at least an hour from now.");
        }
    }

    /** The candidate replies at least two hours before the interview; by default within two days. */
    static Instant replyBy(Instant requested, Instant start, Instant now) {
        Instant latest = start.minus(Duration.ofHours(2));
        Instant wanted = requested != null ? requested : now.plus(Duration.ofDays(2));
        if (!wanted.isAfter(now)) {
            throw ApiException.badRequest("reply_by_past", "The reply deadline must be in the future.");
        }
        return wanted.isBefore(latest) ? wanted : latest;
    }
}
