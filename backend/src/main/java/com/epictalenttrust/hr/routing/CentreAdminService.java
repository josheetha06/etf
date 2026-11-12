package com.epictalenttrust.hr.routing;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.security.CurrentUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partner centres under Settings. A company's HR admins manage their own centres: details, assessors and the time
 * slots candidates can be booked into. Centres from ETT's shared network are listed too, read-only.
 */
@Service
public class CentreAdminService {

    public record AssessorView(UUID id, String fullName, String email, boolean active) {
    }

    public record SlotView(UUID id, Instant startAt, Instant endAt, int seats, int booked) {
    }

    public record CentreView(UUID id, String name, String address, String city, String postcode, String country,
                             Double latitude, Double longitude, String timeZone, Boolean stepFreeAccess,
                             String partnerOrg, boolean active, boolean editable, List<AssessorView> assessors,
                             int slotsNext4Weeks, int seatsNext4Weeks, int bookedNext4Weeks, Instant nextSlotAt) {
    }

    public record CentreInput(@NotBlank @Size(max = 120) String name, @Size(max = 300) String address,
                              @NotBlank @Size(max = 80) String city, @Size(max = 20) String postcode,
                              @Size(max = 60) String country, @DecimalMin("-90") @DecimalMax("90") Double latitude,
                              @DecimalMin("-180") @DecimalMax("180") Double longitude, @NotBlank String timeZone,
                              Boolean stepFreeAccess, @Size(max = 120) String partnerOrg, Boolean active) {
    }

    public record AssessorInput(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email @Size(max = 200) String email,
                                Boolean active) {
    }

    /** Adds the same times on the chosen weekdays between two dates. */
    public record SlotPattern(@NotNull LocalDate fromDate, @NotNull LocalDate toDate,
                              @NotEmpty List<@Min(1) @Max(7) Integer> weekdays, @NotEmpty @Size(max = 10) List<String> times,
                              @Min(15) @Max(480) int durationMin, @Min(1) @Max(50) int seats) {
    }

    public record SlotResult(int added, int skipped) {
    }

    private final JdbcClient jdbc;
    private final Places places;
    private final ActivityService activity;
    private final Clock clock;

    public CentreAdminService(JdbcClient jdbc, Places places, ActivityService activity, Clock clock) {
        this.jdbc = jdbc;
        this.places = places;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CentreView> list() {
        UUID company = CurrentUser.companyId();
        Map<UUID, List<AssessorView>> assessors = new HashMap<>();
        jdbc.sql("""
                        select a.id, a.centre_id, a.full_name, a.email, a.status = 'active' as active
                          from partner_assessors a join partner_centres c on c.id = a.centre_id
                         where c.company_id is null or c.company_id = :c order by a.full_name
                        """)
                .param("c", company)
                .query((rs, n) -> assessors.computeIfAbsent(Rows.uuid(rs, "centre_id"), k -> new ArrayList<>())
                        .add(new AssessorView(Rows.uuid(rs, "id"), rs.getString("full_name"), rs.getString("email"),
                                rs.getBoolean("active"))))
                .list();
        Instant now = clock.instant();
        return jdbc.sql("""
                        select c.id, c.name, c.address, c.city, c.postcode, c.country, c.latitude, c.longitude, c.time_zone,
                               c.step_free_access, c.partner_org, c.active, c.company_id = :c as editable,
                               (select count(*) from centre_slots s where s.centre_id = c.id and s.start_at between :now and :later) as slots,
                               (select coalesce(sum(seats), 0) from centre_slots s where s.centre_id = c.id and s.start_at between :now and :later) as seats,
                               (select coalesce(sum(booked), 0) from centre_slots s where s.centre_id = c.id and s.start_at between :now and :later) as booked,
                               (select min(start_at) from centre_slots s where s.centre_id = c.id and s.start_at > :now and s.booked < s.seats) as next_slot
                          from partner_centres c
                         where c.company_id is null or c.company_id = :c
                         order by c.company_id is null, c.city, c.name
                        """)
                .param("c", company).param("now", Timestamp.from(now))
                .param("later", Timestamp.from(now.plus(Duration.ofDays(28))))
                .query((rs, n) -> new CentreView(Rows.uuid(rs, "id"), rs.getString("name"), rs.getString("address"),
                        rs.getString("city"), rs.getString("postcode"), rs.getString("country"),
                        (Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"), rs.getString("time_zone"),
                        (Boolean) rs.getObject("step_free_access"), rs.getString("partner_org"), rs.getBoolean("active"),
                        rs.getBoolean("editable"), assessors.getOrDefault(Rows.uuid(rs, "id"), List.of()),
                        rs.getInt("slots"), rs.getInt("seats"), rs.getInt("booked"), Rows.instant(rs, "next_slot")))
                .list();
    }

    @Transactional
    public CentreView create(CentreInput input) {
        Double[] point = point(input);
        UUID id = jdbc.sql("""
                        insert into partner_centres (company_id, name, address, city, postcode, country, latitude, longitude,
                                                     time_zone, step_free_access, partner_org, active, created_by)
                        values (:c, :n, :a, :city, :p, :country, :lat, :lon, :tz, :sf, :o, :active, :by) returning id
                        """)
                .param("c", CurrentUser.companyId()).param("n", input.name().strip()).param("a", blank(input.address()))
                .param("city", input.city().strip()).param("p", blank(input.postcode())).param("country", blank(input.country()))
                .param("lat", point[0]).param("lon", point[1]).param("tz", zone(input.timeZone()))
                .param("sf", input.stepFreeAccess()).param("o", blank(input.partnerOrg()))
                .param("active", input.active() == null || input.active()).param("by", CurrentUser.id())
                .query(UUID.class).single();
        activity.record("centre.created", "partner_centre", id);
        return find(id);
    }

    @Transactional
    public CentreView update(UUID id, CentreInput input) {
        own(id);
        Double[] point = point(input);
        jdbc.sql("""
                update partner_centres set name = :n, address = :a, city = :city, postcode = :p, country = :country,
                       latitude = :lat, longitude = :lon, time_zone = :tz, step_free_access = :sf, partner_org = :o,
                       active = :active
                 where id = :id
                """)
                .param("n", input.name().strip()).param("a", blank(input.address())).param("city", input.city().strip())
                .param("p", blank(input.postcode())).param("country", blank(input.country()))
                .param("lat", point[0]).param("lon", point[1]).param("tz", zone(input.timeZone()))
                .param("sf", input.stepFreeAccess()).param("o", blank(input.partnerOrg()))
                .param("active", input.active() == null || input.active()).param("id", id).update();
        activity.record("centre.changed", "partner_centre", id);
        return find(id);
    }

    @Transactional
    public CentreView addAssessor(UUID centreId, AssessorInput input) {
        own(centreId);
        jdbc.sql("insert into partner_assessors (centre_id, full_name, email, created_by) values (:c, :n, :e, :by)")
                .param("c", centreId).param("n", input.fullName().strip())
                .param("e", input.email().strip().toLowerCase(Locale.ROOT)).param("by", CurrentUser.id()).update();
        return find(centreId);
    }

    @Transactional
    public CentreView updateAssessor(UUID centreId, UUID assessorId, AssessorInput input) {
        own(centreId);
        int n = jdbc.sql("""
                        update partner_assessors set full_name = :n, email = :e,
                               status = cast(:s as assessor_status_t) where id = :id and centre_id = :c
                        """)
                .param("n", input.fullName().strip()).param("e", input.email().strip().toLowerCase(Locale.ROOT))
                .param("s", input.active() == null || input.active() ? "active" : "inactive")
                .param("id", assessorId).param("c", centreId).update();
        if (n == 0) {
            throw ApiException.notFound("Assessor");
        }
        return find(centreId);
    }

    @Transactional(readOnly = true)
    public List<SlotView> slots(UUID centreId) {
        visible(centreId);
        return jdbc.sql("""
                        select id, start_at, end_at, seats, booked from centre_slots
                         where centre_id = :c and start_at > :now order by start_at limit 300
                        """)
                .param("c", centreId).param("now", Timestamp.from(clock.instant()))
                .query((rs, n) -> new SlotView(Rows.uuid(rs, "id"), Rows.instant(rs, "start_at"), Rows.instant(rs, "end_at"),
                        rs.getInt("seats"), rs.getInt("booked")))
                .list();
    }

    /** Adds slots at the same times on the chosen weekdays. Times that already have a slot are skipped. */
    @Transactional
    public SlotResult addSlots(UUID centreId, SlotPattern pattern) {
        own(centreId);
        ZoneId zone = TimeZones.parseOr(jdbc.sql("select time_zone from partner_centres where id = :c").param("c", centreId)
                .query(String.class).single(), TimeZones.INDIA);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (pattern.fromDate().isBefore(today)) {
            throw ApiException.badRequest("date_past", "Slots can only be added from today onwards.");
        }
        if (pattern.toDate().isBefore(pattern.fromDate()) || pattern.toDate().isAfter(pattern.fromDate().plusDays(92))) {
            throw ApiException.badRequest("invalid_range", "Choose an end date after the start, up to three months ahead.");
        }
        List<LocalTime> times = new ArrayList<>();
        for (String t : pattern.times()) {
            try {
                times.add(LocalTime.parse(t.strip()));
            } catch (RuntimeException ex) {
                throw ApiException.badRequest("invalid_time", "Times are 24-hour, like 10:00 or 14:30.");
            }
        }
        int added = 0;
        int skipped = 0;
        for (LocalDate day = pattern.fromDate(); !day.isAfter(pattern.toDate()); day = day.plusDays(1)) {
            if (!pattern.weekdays().contains(day.getDayOfWeek().getValue())) {
                continue;
            }
            for (LocalTime time : times) {
                Instant start = day.atTime(time).atZone(zone).toInstant();
                if (!start.isAfter(clock.instant())) {
                    skipped++;
                    continue;
                }
                int n = jdbc.sql("""
                                insert into centre_slots (centre_id, start_at, end_at, seats, booked)
                                select :c, :s, :e, :seats, 0
                                 where not exists (select 1 from centre_slots where centre_id = :c and start_at = :s)
                                """)
                        .param("c", centreId).param("s", Timestamp.from(start))
                        .param("e", Timestamp.from(start.plus(Duration.ofMinutes(pattern.durationMin()))))
                        .param("seats", pattern.seats()).update();
                if (n == 0) {
                    skipped++;
                } else {
                    added++;
                }
                if (added > 500) {
                    throw ApiException.badRequest("too_many_slots", "That adds over 500 slots. Choose a shorter period.");
                }
            }
        }
        activity.record("centre.slots_added", "partner_centre", centreId);
        return new SlotResult(added, skipped);
    }

    @Transactional
    public void removeSlot(UUID centreId, UUID slotId) {
        own(centreId);
        int n = jdbc.sql("delete from centre_slots where id = :id and centre_id = :c and booked = 0")
                .param("id", slotId).param("c", centreId).update();
        if (n == 0) {
            throw ApiException.conflict("slot_booked", "That slot has bookings, so it can't be removed. Move the interviews first.");
        }
    }

    // ------------------------------------------------------------------ helpers

    private CentreView find(UUID id) {
        return list().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow(() -> ApiException.notFound("Centre"));
    }

    private void own(UUID id) {
        boolean ok = jdbc.sql("select exists (select 1 from partner_centres where id = :id and company_id = :c)")
                .param("id", id).param("c", CurrentUser.companyId()).query(Boolean.class).single();
        if (!ok) {
            throw ApiException.forbidden("not_yours", "This centre is part of ETT's shared network. Ask ETT to change it.");
        }
    }

    private void visible(UUID id) {
        boolean ok = jdbc.sql("select exists (select 1 from partner_centres where id = :id and (company_id is null or company_id = :c))")
                .param("id", id).param("c", CurrentUser.companyId()).query(Boolean.class).single();
        if (!ok) {
            throw ApiException.notFound("Centre");
        }
    }

    /** Uses the coordinates given, or places the city on the map. */
    private Double[] point(CentreInput input) {
        if (input.latitude() != null && input.longitude() != null) {
            return new Double[]{input.latitude(), input.longitude()};
        }
        return places.find(input.city(), input.country())
                .map(p -> new Double[]{p.point().latitude(), p.point().longitude()})
                .orElseThrow(() -> ApiException.badRequest("unknown_city", "We couldn't place " + input.city()
                        + " on the map. Add the centre's latitude and longitude (from any online map)."));
    }

    private static String zone(String id) {
        try {
            return ZoneId.of(id.strip()).getId();
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("invalid_time_zone", "Choose a time zone from the list.");
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
