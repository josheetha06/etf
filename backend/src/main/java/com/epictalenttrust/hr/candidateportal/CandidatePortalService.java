package com.epictalenttrust.hr.candidateportal;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.interview.Interview;
import com.epictalenttrust.hr.interview.InterviewDtos.Availability;
import com.epictalenttrust.hr.interview.InterviewRepository;
import com.epictalenttrust.hr.interview.InterviewStatus;
import com.epictalenttrust.hr.interview.RescheduleReason;
import com.epictalenttrust.hr.interview.RescheduleRequest;
import com.epictalenttrust.hr.interview.RescheduleRequestRepository;
import com.epictalenttrust.hr.interview.RescheduleStatus;
import com.epictalenttrust.hr.interview.TimeOfDay;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.security.ActorContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The candidate's own page, opened from the secure link in their interview emails: see the interview, accept it,
 * ask for a new time with the dates they're free, and message HR. No password: the link is the key.
 */
@Service
public class CandidatePortalService {

    public record InterviewView(UUID id, int round, String roundLabel, RoundKind kind, String status, Instant startAt,
                                String timeZone, Integer durationMin, String format, String meetingLink, String venue,
                                String centreName, String centreAddress, Instant arriveBy, Instant replyBy,
                                boolean canAccept, boolean canAskNewTime, PendingRequest pendingRequest) {
    }

    public record PendingRequest(Instant requestedAt, String reason, List<Availability> availability, String note) {
    }

    public record MessageView(UUID id, String from, boolean fromCandidate, String subject, String body, Instant at) {
    }

    public record Page(String firstName, String jobTitle, String companyName, String timeZone, InterviewView interview,
                       List<MessageView> messages, Instant linkExpiresAt, boolean applicationActive) {
    }

    public record NewTimeInput(@NotNull RescheduleReason reason, @Size(max = 1000) String note,
                               @NotEmpty @Size(max = 5) List<@Valid Availability> availability) {
    }

    public record MessageInput(@NotBlank @Size(max = 3000) String body) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final RescheduleRequestRepository requests;
    private final MessageService messages;
    private final NotificationService notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public CandidatePortalService(JdbcClient jdbc, JobApplicationRepository applications, InterviewRepository interviews,
                                  RescheduleRequestRepository requests, MessageService messages,
                                  NotificationService notifications, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.applications = applications;
        this.interviews = interviews;
        this.requests = requests;
        this.messages = messages;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    private record Link(UUID applicationId, UUID interviewId, Instant expiresAt) {
    }

    @Transactional
    public Page page(String token) {
        Link link = link(token);
        return ActorContext.callAs(new ActorContext.Actor(com.epictalenttrust.hr.activity.ActorKind.CANDIDATE, null),
                () -> build(link));
    }

    @Transactional
    public Page accept(String token) {
        Link link = link(token);
        Interview interview = currentInterview(link);
        if (interview == null || interview.getStatus() != InterviewStatus.INVITED) {
            throw ApiException.conflict("not_invited", "There's no interview waiting for your reply.");
        }
        actAsCandidate(link);
        interview.markConfirmed();
        tellHr(link, interview, NotificationKind.INTERVIEW_ACCEPTED, "%s accepted %s",
                "Accepted on their candidate page. The interview is confirmed.", null);
        return build(link);
    }

    /** The candidate asks for another time and says which dates they're free (up to 5). */
    @Transactional
    public Page askForNewTime(String token, NewTimeInput input) {
        Link link = link(token);
        Interview interview = currentInterview(link);
        if (interview == null || (interview.getStatus() != InterviewStatus.INVITED
                && interview.getStatus() != InterviewStatus.CONFIRMED)) {
            throw ApiException.conflict("not_upcoming", "There's no upcoming interview to move.");
        }
        if (requests.findFirstByInterviewIdAndStatus(interview.getId(), RescheduleStatus.PENDING).isPresent()) {
            throw ApiException.conflict("request_open", "You've already asked for a new time. HR will reply soon.");
        }
        ZoneId zone = TimeZones.parseOr(interview.getCandidateTz(), TimeZones.INDIA);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Availability> slots = new ArrayList<>();
        for (Availability a : input.availability()) {
            if (a.date().isBefore(today.plusDays(1))) {
                throw ApiException.badRequest("date_past", "Choose dates from tomorrow onwards.");
            }
            if (a.date().isAfter(today.plusDays(60))) {
                throw ApiException.badRequest("date_far", "Choose dates within the next two months.");
            }
            String window = a.window().strip().toLowerCase(Locale.ROOT);
            if (!List.of("morning", "afternoon", "any", "range").contains(window)) {
                throw ApiException.badRequest("invalid_window", "Choose morning, afternoon, any time, or a time range.");
            }
            String from = null;
            String to = null;
            if ("range".equals(window)) {
                LocalTime f = parseTime(a.from());
                LocalTime t = parseTime(a.to());
                if (f == null || t == null || !t.isAfter(f.plusMinutes(29))) {
                    throw ApiException.badRequest("invalid_range", "For a time range, give a start and an end at least 30 minutes apart.");
                }
                from = f.toString();
                to = t.toString();
            }
            if (slots.stream().anyMatch(s -> s.date().equals(a.date()))) {
                throw ApiException.badRequest("duplicate_date", "Each date can be added once.");
            }
            slots.add(new Availability(a.date(), window, from, to));
        }
        slots.sort(java.util.Comparator.comparing(Availability::date));
        actAsCandidate(link);
        TimeOfDay common = slots.stream().map(Availability::window).distinct().count() == 1
                ? switch (slots.getFirst().window()) {
                    case "morning" -> TimeOfDay.MORNING;
                    case "afternoon" -> TimeOfDay.AFTERNOON;
                    default -> TimeOfDay.ANY;
                } : TimeOfDay.ANY;
        String note = input.note() == null || input.note().isBlank() ? null : input.note().strip();
        RescheduleRequest request = requests.save(new RescheduleRequest(interview.getId(), input.reason(), note,
                slots.stream().map(Availability::date).toList(), common));
        requests.flush();
        jdbc.sql("update reschedule_requests set availability = cast(:a as jsonb), requested_by_candidate = true where id = :id")
                .param("a", json.writeValueAsString(slots)).param("id", request.getId()).update();
        interview.markRescheduleRequested();
        JobApplication application = applications.findById(link.applicationId()).orElseThrow();
        String dates = slots.stream().map(this::describe).collect(Collectors.joining("; "));
        var message = messages.recordFromCandidate(application, interview.getId(), interview.getScheduledBy(),
                "Asked for a new time · " + roundLabel(interview),
                "Reason: " + label(input.reason()) + (note == null ? "" : "\n\n" + note) + "\n\nFree: " + dates);
        markPortal(message.getId());
        tellHr(link, interview, NotificationKind.RESCHEDULE_REQUESTED, "%s asked to move %s",
                "Free: " + dates + ". Pick a time on one of these dates, or keep the original.", request.getId());
        return build(link);
    }

    @Transactional
    public Page message(String token, MessageInput input) {
        Link link = link(token);
        JobApplication application = applications.findById(link.applicationId()).orElseThrow();
        Interview interview = currentInterview(link);
        actAsCandidate(link);
        UUID to = interview != null && interview.getScheduledBy() != null ? interview.getScheduledBy() : owner(application);
        var message = messages.recordFromCandidate(application, interview == null ? null : interview.getId(), to,
                "Message from the candidate page", input.body().strip());
        markPortal(message.getId());
        record Job(UUID id, UUID companyId, String title, String name, UUID owner, UUID manager) {
        }
        Job job = jdbc.sql("""
                        select j.id, j.company_id, j.title, c.full_name, coalesce(j.owner_id, j.created_by) as owner,
                               j.hiring_manager_id
                          from applications a join jobs j on j.id = a.job_id
                          join candidates c on c.id = a.candidate_id where a.id = :a
                        """)
                .param("a", application.getId())
                .query((rs, n) -> new Job(Rows.uuid(rs, "id"), Rows.uuid(rs, "company_id"), rs.getString("title"),
                        rs.getString("full_name"), Rows.uuid(rs, "owner"), Rows.uuid(rs, "hiring_manager_id"))).single();
        // The job's owner and hiring manager both see it in the bell (and anyone covering for them); each gets an
        // email too if their alert settings say so.
        notifications.notify(new NotificationService.Request(job.companyId(), Arrays.asList(to, job.owner(), job.manager()),
                NotificationKind.CANDIDATE_MESSAGE,
                "New message from " + job.name(), shorten(input.body().strip()) + " · " + job.title(),
                "/applications/" + application.getId() + "?tab=messages", job.id(), application.getId(),
                interview == null ? null : interview.getId()));
        return build(link);
    }

    // ------------------------------------------------------------------ helpers

    private Page build(Link link) {
        record Info(String name, String job, String company, boolean active, String country) {
        }
        Info info = jdbc.sql("""
                        select c.full_name, j.title, co.name, a.status = 'active' as active, c.country
                          from applications a join jobs j on j.id = a.job_id join companies co on co.id = j.company_id
                          join candidates c on c.id = a.candidate_id where a.id = :a
                        """)
                .param("a", link.applicationId())
                .query((rs, n) -> new Info(rs.getString("full_name"), rs.getString("title"), rs.getString("name"),
                        rs.getBoolean("active"), rs.getString("country"))).single();
        Interview interview = currentInterview(link);
        InterviewView view = interview == null ? null : interviewView(interview, info.active());
        ZoneId zone = interview != null ? TimeZones.parseOr(interview.getCandidateTz(), TimeZones.forCountry(info.country()))
                : TimeZones.forCountry(info.country());
        List<MessageView> thread = jdbc.sql("""
                        select id, direction, subject, body, coalesce(sent_at, created_at) as at
                          from messages where application_id = :a and (direction = 'inbound' or sent_at is not null)
                         order by coalesce(sent_at, created_at) desc limit 30
                        """)
                .param("a", link.applicationId())
                .query((rs, n) -> {
                    boolean mine = "inbound".equals(rs.getString("direction"));
                    return new MessageView(Rows.uuid(rs, "id"), mine ? "You" : info.company(), mine,
                            rs.getString("subject"), rs.getString("body"), Rows.instant(rs, "at"));
                })
                .list();
        return new Page(info.name().strip().split("\\s+")[0], info.job(), info.company(), zone.getId(), view, thread,
                link.expiresAt(), info.active());
    }

    private InterviewView interviewView(Interview interview, boolean active) {
        record Plan(String name, RoundKind kind, String centre, String address) {
        }
        Plan plan = jdbc.sql("""
                        select jr.name, jr.kind, pc.name as centre, concat_ws(', ', pc.address, pc.city, pc.postcode) as address
                          from interviews i join applications a on a.id = i.application_id
                          left join job_rounds jr on jr.job_id = a.job_id and jr.round = i.round
                          left join centre_slots cs on cs.id = i.slot_id left join partner_centres pc on pc.id = cs.centre_id
                         where i.id = :i
                        """)
                .param("i", interview.getId())
                .query((rs, n) -> new Plan(rs.getString("name"), Rows.enumOf(rs, "kind", RoundKind.class),
                        rs.getString("centre"), rs.getString("address"))).single();
        var pending = requests.findFirstByInterviewIdAndStatus(interview.getId(), RescheduleStatus.PENDING)
                .map(r -> new PendingRequest(r.getCreatedAt(), label(r.getReason()), availability(r.getId()), r.getNote()))
                .orElse(null);
        String format = plan.kind() == RoundKind.AI_INTERVIEW ? "Online interview with our AI interviewer"
                : plan.kind() == RoundKind.PARTNER ? "In person at a partner assessment centre"
                : interview.getVenue() != null ? "In person with the interview panel" : "Online video call with the interview panel";
        boolean upcoming = interview.getStartAt() != null && interview.getStartAt().isAfter(clock.instant());
        return new InterviewView(interview.getId(), interview.getRound(),
                MessageTemplates.label(plan.name(), interview.getRound()), plan.kind(),
                interview.getStatus().name().toLowerCase(Locale.ROOT), interview.getStartAt(), interview.getCandidateTz(),
                interview.getDurationMin(), format, interview.getMeetingLink(), interview.getVenue(), plan.centre(),
                plan.address(), interview.getArriveBy(), interview.getReplyBy(),
                active && upcoming && interview.getStatus() == InterviewStatus.INVITED,
                active && upcoming && pending == null && (interview.getStatus() == InterviewStatus.INVITED
                        || interview.getStatus() == InterviewStatus.CONFIRMED),
                pending);
    }

    private List<Availability> availability(UUID requestId) {
        String text = jdbc.sql("select availability::text from reschedule_requests where id = :id").param("id", requestId)
                .query(String.class).optional().orElse(null);
        return text == null ? List.of()
                : json.readValue(text, new tools.jackson.core.type.TypeReference<List<Availability>>() {
                });
    }

    /** The interview the link is about, or the application's latest upcoming one. */
    private Interview currentInterview(Link link) {
        UUID id = jdbc.sql("""
                        select id from interviews where application_id = :a and status not in ('cancelled', 'draft')
                         order by case when id = :i then 0 else 1 end, start_at desc nulls last limit 1
                        """)
                .param("a", link.applicationId()).param("i", link.interviewId())
                .query(UUID.class).optional().orElse(null);
        return id == null ? null : interviews.findById(id).orElse(null);
    }

    private Link link(String token) {
        if (token == null || token.length() < 20 || token.length() > 80) {
            throw ApiException.notFound("Page");
        }
        Link link = jdbc.sql("""
                        select application_id, interview_id, expires_at from candidate_links
                         where token_hash = :h and expires_at > now()
                        """)
                .param("h", CandidateLinks.hash(token))
                .query((rs, n) -> new Link(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "interview_id"),
                        Rows.instant(rs, "expires_at")))
                .optional()
                .orElseThrow(() -> ApiException.notFound("Page"));
        jdbc.sql("update candidate_links set last_used_at = now() where token_hash = :h")
                .param("h", CandidateLinks.hash(token)).update();
        return link;
    }

    private void actAsCandidate(Link link) {
        String name = jdbc.sql("select c.full_name from applications a join candidates c on c.id = a.candidate_id where a.id = :a")
                .param("a", link.applicationId()).query(String.class).single();
        jdbc.sql("""
                select set_config('app.current_actor_kind', 'candidate', true),
                       set_config('app.current_user_id', '', true),
                       set_config('app.actor_label', :l, true)
                """)
                .param("l", "Candidate: " + name).query((rs, n) -> 0).list();
    }

    private void markPortal(UUID messageId) {
        jdbc.sql("update messages set channel = 'portal' where id = :id").param("id", messageId).update();
    }

    private void tellHr(Link link, Interview interview, NotificationKind kind, String titleFormat, String body,
                        UUID requestId) {
        record Job(UUID id, UUID companyId, String title, String name, UUID owner, UUID manager) {
        }
        Job job = jdbc.sql("""
                        select j.id, j.company_id, j.title, c.full_name, coalesce(j.owner_id, j.created_by) as owner,
                               j.hiring_manager_id
                          from applications a join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                         where a.id = :a
                        """)
                .param("a", link.applicationId())
                .query((rs, n) -> new Job(Rows.uuid(rs, "id"), Rows.uuid(rs, "company_id"), rs.getString("title"),
                        rs.getString("full_name"), Rows.uuid(rs, "owner"), Rows.uuid(rs, "hiring_manager_id"))).single();
        String applicationLink = "/applications/" + link.applicationId() + (requestId == null ? "" : "?reschedule=" + requestId);
        notifications.notify(new NotificationService.Request(job.companyId(),
                Arrays.asList(interview.getScheduledBy(), job.owner(), job.manager()), kind,
                titleFormat.formatted(job.name(), roundLabel(interview)), body + " · " + job.title(), applicationLink,
                job.id(), link.applicationId(), interview.getId()));
    }

    private String roundLabel(Interview interview) {
        String name = jdbc.sql("""
                        select jr.name from job_rounds jr join applications a on a.job_id = jr.job_id
                         where a.id = :a and jr.round = :r
                        """)
                .param("a", interview.getApplicationId()).param("r", interview.getRound())
                .query(String.class).optional().orElse(null);
        return MessageTemplates.label(name, interview.getRound());
    }

    private UUID owner(JobApplication application) {
        return jdbc.sql("select coalesce(owner_id, created_by) from jobs where id = :j").param("j", application.getJobId())
                .query(UUID.class).single();
    }

    private String describe(Availability a) {
        String when = switch (a.window()) {
            case "morning" -> "morning";
            case "afternoon" -> "afternoon";
            case "range" -> a.from() + " to " + a.to();
            default -> "any time";
        };
        return DAY.format(a.date()) + ", " + when;
    }

    private static LocalTime parseTime(String value) {
        try {
            return value == null ? null : LocalTime.parse(value.strip());
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    private static String label(RescheduleReason reason) {
        return switch (reason) {
            case CLASH -> "Clash with another commitment";
            case ILLNESS -> "Illness";
            case TRAVEL -> "Travel";
            case OTHER -> "Other";
        };
    }

    private static String shorten(String text) {
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() > 120 ? flat.substring(0, 117) + "…" : flat;
    }
}
