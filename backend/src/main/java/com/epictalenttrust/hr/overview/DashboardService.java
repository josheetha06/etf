package com.epictalenttrust.hr.overview;

import com.epictalenttrust.hr.application.PipelineQueries;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.security.Access;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.security.HrUserPrincipal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Overview: what needs attention (round reports, shortlist decisions, offers, overdue rounds), who to book,
 * the newest candidate messages, and today's interviews. Everything is limited to the jobs the signed-in person can
 * see (their own, and any they're covering); closed jobs are left out. CVs with missing details or red flags are
 * handled in each job's AI shortlisting, not here.
 */
@Service
public class DashboardService {

    /** One thing to do. {@code link} is the page that does it. */
    public record ToDo(String kind, String title, String detail, String link, Instant at, boolean overdue) {
    }

    /** A to-do card: how many things, and the first few (overdue first). */
    public record Group(String key, int count, List<ToDo> items) {
    }

    /**
     * @param onPanel     the signed-in person is on this interview's panel (their email matches a panel member's)
     * @param meetingLink the online meeting link, only for interviews the signed-in person is on the panel of
     */
    public record TodayInterview(UUID interviewId, UUID applicationId, Instant startAt, Integer durationMin,
                                 String candidateName, String jobTitle, String roundLabel, RoundKind kind, String mode,
                                 String where, int panelSize, String status, boolean onPanel, String meetingLink) {
    }

    /** One unread message from a candidate. */
    public record MessagePreview(UUID id, UUID applicationId, String candidateName, String jobTitle, String subject,
                                 String preview, Instant at) {
    }

    public record Messages(int unread, List<MessagePreview> latest) {
    }

    public record Dashboard(String firstName, String companyName, String timeZone, Group needsAttention, Group toBook,
                            Messages messages, List<TodayInterview> today, List<String> covering) {
    }

    private final JdbcClient jdbc;
    private final Access access;
    private final CompanyRepository companies;
    private final Clock clock;

    public DashboardService(JdbcClient jdbc, Access access, CompanyRepository companies, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.companies = companies;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(String zoneParam) {
        HrUserPrincipal me = CurrentUser.get();
        var company = companies.findById(me.companyId()).orElseThrow();
        String companyZone = jdbc.sql("select time_zone from companies where id = :c").param("c", me.companyId())
                .query(String.class).optional().orElse(null);
        ZoneId zone = TimeZones.parseOr(zoneParam, TimeZones.parseOr(companyZone, TimeZones.forCountry(company.getCountry())));
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        Instant now = clock.instant();

        List<Row> rows = access.bind(jdbc.sql(PipelineQueries.PIPELINE + """
                        select p.application_id, p.job_id, p.job_title, p.full_name, p.next_action, p.pending_request_id,
                               p.current_round, p.round_name, p.due_at, p.job_status,
                               coalesce(p.report_ready_at, p.stage_entered_at, p.added_at) as at
                          from pipeline p where p.status = 'active' and p.job_status not in ('closed', 'archived')
                        """))
                .query((rs, n) -> new Row(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "job_id"),
                        rs.getString("job_title"), rs.getString("full_name"), rs.getString("next_action"),
                        Rows.uuid(rs, "pending_request_id"), Rows.integer(rs, "current_round"),
                        rs.getString("round_name"), Rows.instant(rs, "due_at"), rs.getString("job_status"),
                        Rows.instant(rs, "at")))
                .list();

        List<ToDo> attention = new ArrayList<>();
        List<ToDo> book = new ArrayList<>();
        // Shortlists: one item per job.
        perJob(rows, "decide_shortlist").forEach((job, list) -> attention.add(new ToDo("shortlist",
                "Shortlist: " + list.getFirst().jobTitle(),
                list.size() + (list.size() == 1 ? " CV waits" : " CVs wait") + " for your decision",
                "/jobs/" + job + "?tab=shortlist", earliest(list), false)));
        for (Row r : rows) {
            boolean overdue = r.dueAt() != null && r.dueAt().isBefore(now);
            String round = MessageTemplates.label(r.roundName(), r.currentRound() == null ? 0 : r.currentRound());
            switch (r.action()) {
                case "decide_round" -> attention.add(new ToDo("round_report", r.name(),
                        round + " report ready · " + r.jobTitle(), "/applications/" + r.applicationId(), r.at(), overdue));
                case "record_offer" -> attention.add(new ToDo("offer", r.name(),
                        "Record the offer outcome · " + r.jobTitle(), "/applications/" + r.applicationId(), r.at(), false));
                case "answer_reschedule" -> book.add(new ToDo("reschedule", r.name(),
                        "Asked for a new time · " + round + " · " + r.jobTitle(),
                        "/applications/" + r.applicationId() + "?reschedule=" + r.requestId(), r.at(), overdue));
                case "schedule_round" -> {
                    // A job on hold can't book new interviews, so it waits until the job is open again.
                    if (!"on_hold".equals(r.jobStatus())) {
                        book.add(new ToDo("book", r.name(), "Book " + round + " · " + r.jobTitle(),
                                "/applications/" + r.applicationId(), r.at(), overdue));
                    }
                }
                default -> {
                    if (overdue && "waiting".equals(r.action())) {
                        attention.add(new ToDo("overdue", r.name(),
                                round + " past its target time · " + r.jobTitle(),
                                "/applications/" + r.applicationId(), r.dueAt(), true));
                    }
                }
            }
        }

        int unread = access.bind(jdbc.sql("""
                        select count(*) from messages m join applications a on a.id = m.application_id
                          join jobs j on j.id = a.job_id
                         where j.company_id = :companyId and m.direction = 'inbound' and m.read_at is null
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                        """)).query(Integer.class).single();
        List<MessagePreview> latest = access.bind(jdbc.sql("""
                        select m.id, m.application_id, c.full_name, j.title, m.subject, m.body, m.created_at
                          from messages m join applications a on a.id = m.application_id
                          join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                         where j.company_id = :companyId and m.direction = 'inbound' and m.read_at is null
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                         order by m.created_at desc limit 3
                        """))
                .query((rs, n) -> new MessagePreview(Rows.uuid(rs, "id"), Rows.uuid(rs, "application_id"),
                        rs.getString("full_name"), rs.getString("title"), rs.getString("subject"),
                        preview(rs.getString("body")), Rows.instant(rs, "created_at")))
                .list();

        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();
        String myEmail = me.email() == null ? "" : me.email().strip().toLowerCase(java.util.Locale.ROOT);
        List<TodayInterview> todays = access.bind(jdbc.sql("""
                        select i.id, i.application_id, i.start_at, i.duration_min, c.full_name, j.title, jr.name as round_name,
                               i.round, jr.kind, i.mode, i.status, i.meeting_link,
                               coalesce(pc.name, i.venue, case when i.meeting_link is not null then 'Online' end) as where_text,
                               (select count(*) from panel_members m where m.interview_id = i.id) as panel,
                               exists (select 1 from panel_members m where m.interview_id = i.id
                                          and lower(m.email) = :me) as on_panel
                          from interviews i join applications a on a.id = i.application_id
                          join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                          left join job_rounds jr on jr.job_id = j.id and jr.round = i.round
                          left join centre_slots cs on cs.id = i.slot_id left join partner_centres pc on pc.id = cs.centre_id
                         where j.company_id = :companyId and i.start_at >= :from and i.start_at < :to
                           and i.status in ('invited', 'confirmed', 'reschedule_requested', 'completed')
                           and j.status not in ('closed', 'archived')
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people)
                                or exists (select 1 from panel_members m where m.interview_id = i.id and lower(m.email) = :me))
                         order by i.start_at limit 20
                        """))
                .param("from", Timestamp.from(dayStart)).param("to", Timestamp.from(dayEnd)).param("me", myEmail)
                .query((rs, n) -> {
                    boolean onPanel = rs.getBoolean("on_panel");
                    String link = rs.getString("meeting_link");
                    return new TodayInterview(Rows.uuid(rs, "id"), Rows.uuid(rs, "application_id"),
                            Rows.instant(rs, "start_at"), Rows.integer(rs, "duration_min"), rs.getString("full_name"),
                            rs.getString("title"), MessageTemplates.label(rs.getString("round_name"), rs.getInt("round")),
                            Rows.enumOf(rs, "kind", RoundKind.class), rs.getString("mode"), rs.getString("where_text"),
                            rs.getInt("panel"), rs.getString("status"), onPanel,
                            onPanel && link != null && !link.isBlank() ? link : null);
                })
                .list();
        List<String> covering = access.scope().covering().isEmpty() ? List.of()
                : jdbc.sql("select full_name from hr_users where id in (:ids) order by full_name")
                        .param("ids", access.scope().covering()).query(String.class).list();
        return new Dashboard(me.fullName().split("\\s+")[0], company.getName(), zone.getId(),
                group("needs_attention", attention), group("to_book", book), new Messages(unread, latest), todays, covering);
    }

    private static String preview(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ").strip();
        return flat.length() <= 140 ? flat : flat.substring(0, 137).strip() + "…";
    }

    private record Row(UUID applicationId, UUID jobId, String jobTitle, String name, String action, UUID requestId,
                       Integer currentRound, String roundName, Instant dueAt, String jobStatus, Instant at) {
    }

    private static java.util.Map<UUID, List<Row>> perJob(List<Row> rows, String action) {
        java.util.Map<UUID, List<Row>> out = new java.util.LinkedHashMap<>();
        rows.stream().filter(r -> action.equals(r.action()))
                .forEach(r -> out.computeIfAbsent(r.jobId(), k -> new ArrayList<>()).add(r));
        return out;
    }

    private static Instant earliest(List<Row> rows) {
        return rows.stream().map(Row::at).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
    }

    /** Overdue first, then oldest first. */
    private static Group group(String key, List<ToDo> items) {
        List<ToDo> sorted = items.stream()
                .sorted(Comparator.comparing((ToDo t) -> !t.overdue())
                        .thenComparing(t -> t.at() == null ? Instant.MAX : t.at()))
                .toList();
        return new Group(key, sorted.size(), sorted.size() > 50 ? sorted.subList(0, 50) : sorted);
    }
}
