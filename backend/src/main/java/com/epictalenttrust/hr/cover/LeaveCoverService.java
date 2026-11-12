package com.epictalenttrust.hr.cover;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.mail.Outbox;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.security.HrUserPrincipal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Leave cover: while someone is away, a colleague they choose sees and works on all their jobs, and gets their
 * notifications. Cover starts and ends by itself on the dates set. Only job access passes over (the colleague keeps
 * their own role), and cover isn't passed on again.
 */
@Service
public class LeaveCoverService {

    public record CoverInput(UUID userId, @NotNull UUID coverUserId, @NotNull LocalDate startsOn,
                             @NotNull LocalDate endsOn, @Size(max = 500) String note) {
    }

    public record DatesInput(@NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }

    public record CoverView(UUID id, UUID userId, String userName, UUID coverUserId, String coverUserName,
                            LocalDate startsOn, LocalDate endsOn, String note, String state, boolean canChange,
                            String createdByName) {
    }

    public record Person(UUID id, String fullName, String role) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final int MAX_DAYS = 120;

    private final JdbcClient jdbc;
    private final NotificationService notifications;
    private final Outbox outbox;
    private final ActivityService activity;
    private final EttProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public LeaveCoverService(JdbcClient jdbc, NotificationService notifications, Outbox outbox, ActivityService activity,
                             EttProperties properties, TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.outbox = outbox;
        this.activity = activity;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    /** My own leave, cover I'm doing for others, and (for HR admins) everyone's current and upcoming cover. */
    @Transactional(readOnly = true)
    public List<CoverView> list() {
        HrUserPrincipal me = CurrentUser.get();
        LocalDate today = today(me.companyId());
        return jdbc.sql("""
                        select lc.id, lc.user_id, u.full_name as user_name, lc.cover_user_id, cu.full_name as cover_name,
                               lc.starts_on, lc.ends_on, lc.note, lc.ended_early_at, cb.full_name as created_by_name
                          from leave_cover lc join hr_users u on u.id = lc.user_id
                          join hr_users cu on cu.id = lc.cover_user_id
                          left join hr_users cb on cb.id = lc.created_by
                         where lc.company_id = :c and (lc.user_id = :me or lc.cover_user_id = :me or :admin)
                           and lc.ends_on >= :past
                         order by lc.starts_on desc
                        """)
                .param("c", me.companyId()).param("me", me.id()).param("admin", me.role().seesAllJobs())
                .param("past", today.minusDays(30))
                .query((rs, n) -> {
                    LocalDate start = Rows.date(rs, "starts_on");
                    LocalDate end = Rows.date(rs, "ends_on");
                    boolean ended = rs.getTimestamp("ended_early_at") != null;
                    String state = ended ? "ended" : today.isBefore(start) ? "upcoming" : today.isAfter(end) ? "finished" : "active";
                    UUID owner = Rows.uuid(rs, "user_id");
                    return new CoverView(Rows.uuid(rs, "id"), owner, rs.getString("user_name"), Rows.uuid(rs, "cover_user_id"),
                            rs.getString("cover_name"), start, end, rs.getString("note"), state,
                            ("upcoming".equals(state) || "active".equals(state)) && (owner.equals(me.id()) || me.role().seesAllJobs()),
                            rs.getString("created_by_name"));
                })
                .list();
    }

    /** Colleagues who can cover: active HR admins, recruiters and hiring managers (not the panel login). */
    @Transactional(readOnly = true)
    public List<Person> colleagues() {
        return jdbc.sql("""
                        select id, full_name, role from hr_users
                         where company_id = :c and status = 'active' and role in ('admin', 'recruiter', 'hiring_manager')
                         order by full_name
                        """)
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> new Person(Rows.uuid(rs, "id"), rs.getString("full_name"), rs.getString("role"))).list();
    }

    @Transactional
    public CoverView create(CoverInput input) {
        HrUserPrincipal me = CurrentUser.get();
        UUID userId = input.userId() == null ? me.id() : input.userId();
        if (!userId.equals(me.id()) && !me.role().seesAllJobs()) {
            throw ApiException.forbidden("admin_only", "Only HR admins can set up cover for someone else.");
        }
        requireColleague(userId, "The person going on leave");
        requireColleague(input.coverUserId(), "The colleague covering");
        if (userId.equals(input.coverUserId())) {
            throw ApiException.badRequest("same_person", "Choose someone else to cover.");
        }
        checkDates(me.companyId(), input.startsOn(), input.endsOn(), true);
        if (overlaps(userId, null, input.startsOn(), input.endsOn(), "user_id")) {
            throw ApiException.conflict("already_covered", "There's already cover set for some of these dates.");
        }
        if (overlaps(input.coverUserId(), null, input.startsOn(), input.endsOn(), "user_id")) {
            throw ApiException.conflict("cover_on_leave", nameOf(input.coverUserId())
                    + " is on leave for some of these dates. Choose someone else.");
        }
        if (overlaps(userId, null, input.startsOn(), input.endsOn(), "cover_user_id")) {
            throw ApiException.conflict("covering_others", nameOf(userId)
                    + " is covering for someone else then. Ask an HR admin to hand that cover to another colleague first.");
        }
        UUID id = jdbc.sql("""
                        insert into leave_cover (company_id, user_id, cover_user_id, starts_on, ends_on, note, created_by)
                        values (:c, :u, :cu, :s, :e, :n, :by) returning id
                        """)
                .param("c", me.companyId()).param("u", userId).param("cu", input.coverUserId())
                .param("s", input.startsOn()).param("e", input.endsOn())
                .param("n", input.note() == null || input.note().isBlank() ? null : input.note().strip())
                .param("by", me.id()).query(UUID.class).single();
        activity.record("leave_cover.created", "hr_user", userId);
        tellCover(id, false);
        return find(id);
    }

    @Transactional
    public CoverView changeDates(UUID id, DatesInput input) {
        CoverView cover = editable(id);
        HrUserPrincipal me = CurrentUser.get();
        checkDates(me.companyId(), input.startsOn(), input.endsOn(), !"active".equals(cover.state()));
        if ("active".equals(cover.state()) && !input.startsOn().equals(cover.startsOn())) {
            throw ApiException.badRequest("already_started", "Cover has already started, so only the end date can change.");
        }
        if (overlaps(cover.userId(), id, input.startsOn(), input.endsOn(), "user_id")) {
            throw ApiException.conflict("already_covered", "There's already cover set for some of these dates.");
        }
        jdbc.sql("update leave_cover set starts_on = :s, ends_on = :e where id = :id")
                .param("s", input.startsOn()).param("e", input.endsOn()).param("id", id).update();
        activity.record("leave_cover.changed", "hr_user", cover.userId());
        tellCover(id, true);
        return find(id);
    }

    /** Ends cover now (back early) or cancels cover that hasn't started. */
    @Transactional
    public CoverView end(UUID id) {
        CoverView cover = editable(id);
        jdbc.sql("update leave_cover set ended_early_at = now() where id = :id").param("id", id).update();
        activity.record("leave_cover.ended", "hr_user", cover.userId());
        return find(id);
    }

    /** Each morning: tell colleagues whose cover starts today (once). */
    @Scheduled(cron = "0 5 * * * *")
    public void coverStarting() {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> transaction.executeWithoutResult(status ->
                jdbc.sql("""
                                select lc.id from leave_cover lc join companies co on co.id = lc.company_id
                                 where lc.start_notified_at is null and lc.ended_early_at is null
                                   and (now() at time zone co.time_zone)::date between lc.starts_on and lc.ends_on
                                """)
                        .query(UUID.class).list().forEach(id -> tellCover(id, false))));
    }

    // ------------------------------------------------------------------ helpers

    private void tellCover(UUID id, boolean changed) {
        record Row(UUID companyId, UUID userId, String userName, UUID coverId, String coverName, String coverEmail,
                   LocalDate start, LocalDate end, String note, boolean notified) {
        }
        Row row = jdbc.sql("""
                        select lc.company_id, lc.user_id, u.full_name as un, lc.cover_user_id, cu.full_name as cn,
                               cu.work_email, lc.starts_on, lc.ends_on, lc.note, lc.start_notified_at is not null as notified
                          from leave_cover lc join hr_users u on u.id = lc.user_id join hr_users cu on cu.id = lc.cover_user_id
                         where lc.id = :id
                        """)
                .param("id", id)
                .query((rs, n) -> new Row(Rows.uuid(rs, "company_id"), Rows.uuid(rs, "user_id"), rs.getString("un"),
                        Rows.uuid(rs, "cover_user_id"), rs.getString("cn"), rs.getString("work_email"),
                        Rows.date(rs, "starts_on"), Rows.date(rs, "ends_on"), rs.getString("note"), rs.getBoolean("notified")))
                .single();
        LocalDate today = today(row.companyId());
        boolean started = !today.isBefore(row.start());
        String period = DAY.format(row.start()) + " to " + DAY.format(row.end());
        if (!started && !changed) {
            notifications.notify(new NotificationService.Request(row.companyId(), List.of(row.coverId()),
                    NotificationKind.COVER_STARTED, "You'll cover for " + row.userName() + " from " + DAY.format(row.start()),
                    "From " + period + " you'll see all of " + first(row.userName()) + "'s jobs and get their notifications.",
                    "/settings?tab=leave", null, null, null));
            return;
        }
        if (row.notified() && !changed) {
            return;
        }
        String body = """
                Hello %s,

                %s is on leave from %s. Until then you can see and work on all of their jobs, candidates and interviews in the HR portal, and you'll get their notifications.
                %s
                Open the portal: %s

                Thank you""".formatted(first(row.coverName()), row.userName(), period,
                row.note() == null ? "" : "\nNote from " + first(row.userName()) + ": " + row.note() + "\n",
                properties.portalUrl());
        outbox.queue(new Outbox.Email(row.companyId(), null, null, "cover_started", row.coverEmail(), row.coverName(),
                (changed ? "Cover dates changed: " : "You're covering for ") + row.userName(), body));
        notifications.notify(new NotificationService.Request(row.companyId(), List.of(row.coverId()),
                NotificationKind.COVER_STARTED, "You're covering for " + row.userName() + " until " + DAY.format(row.end()),
                "Their jobs now appear in your lists, marked as covered.", "/jobs", null, null, null));
        jdbc.sql("update leave_cover set start_notified_at = now() where id = :id").param("id", id).update();
    }

    private CoverView editable(UUID id) {
        CoverView cover = list().stream().filter(c -> c.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Cover"));
        if (!cover.canChange()) {
            throw ApiException.conflict("cover_closed", "This cover has finished, or you can't change it.");
        }
        return cover;
    }

    private CoverView find(UUID id) {
        return list().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("Cover"));
    }

    private void checkDates(UUID companyId, LocalDate start, LocalDate end, boolean startInFuture) {
        LocalDate today = today(companyId);
        if (startInFuture && start.isBefore(today)) {
            throw ApiException.badRequest("start_past", "Cover can't start in the past.");
        }
        if (end.isBefore(start)) {
            throw ApiException.badRequest("end_before_start", "The end date must be on or after the start date.");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_DAYS) {
            throw ApiException.badRequest("too_long", "Cover can be set for up to " + MAX_DAYS + " days at a time.");
        }
    }

    private boolean overlaps(UUID person, UUID except, LocalDate start, LocalDate end, String column) {
        return jdbc.sql("""
                        select exists (select 1 from leave_cover where %s = :p and ended_early_at is null
                                          and (cast(:x as uuid) is null or id <> :x)
                                          and starts_on <= :e and ends_on >= :s)
                        """.formatted(column))
                .param("p", person).param("x", except).param("s", start).param("e", end)
                .query(Boolean.class).single();
    }

    private void requireColleague(UUID id, String who) {
        boolean ok = jdbc.sql("""
                        select exists (select 1 from hr_users where id = :u and company_id = :c and status = 'active'
                                          and role in ('admin', 'recruiter', 'hiring_manager'))
                        """)
                .param("u", id).param("c", CurrentUser.companyId()).query(Boolean.class).single();
        if (!ok) {
            throw ApiException.badRequest("invalid_person", who + " must be an active HR admin, recruiter or hiring manager.");
        }
    }

    private String nameOf(UUID id) {
        return jdbc.sql("select full_name from hr_users where id = :u").param("u", id).query(String.class).single();
    }

    private LocalDate today(UUID companyId) {
        String zone = jdbc.sql("select time_zone from companies where id = :c").param("c", companyId)
                .query(String.class).optional().orElse(null);
        ZoneId z = TimeZones.parseOr(zone, TimeZones.INDIA);
        return LocalDate.now(clock.withZone(z));
    }

    private static String first(String name) {
        return name == null ? "there" : name.strip().split("\\s+")[0];
    }
}
