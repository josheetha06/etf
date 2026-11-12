package com.epictalenttrust.hr.routing;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.routing.CentreRouter.Preference;
import com.epictalenttrust.hr.routing.CentreRouter.TimeOfDay;
import com.epictalenttrust.hr.routing.Places.Place;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The partner assessment centres HR can choose from for a candidate, with the reasons: only the centres in the
 * candidate's nearest centre city, taking turns (see {@link CentreRouter}), and other cities on request for
 * exceptions. {@link #recordTurn} moves a centre to the back of its city's queue when a candidate is booked there.
 */
@Service
public class CentreOptionsService {

    public record SlotView(UUID id, Instant startAt, Instant endAt, int seatsLeft, List<UUID> freeAssessorIds) {
    }

    public record AssessorView(UUID id, String fullName) {
    }

    public record CentreView(UUID id, String name, String address, String city, String postcode, Boolean stepFreeAccess,
                             String partnerOrg, String timeZone, boolean inNearestCity, boolean recommended,
                             Double roadKm, Integer travelMinutes, UUID firstSuitableSlotId,
                             boolean inRotation, Integer turn, Instant lastAssignedAt,
                             List<String> reasons, List<SlotView> slots, List<AssessorView> assessors) {
    }

    /**
     * Where the candidate is.
     *
     * @param text   what the CV, the AI interviewer or HR said
     * @param label  the place it was matched to on the map, or null if it couldn't be placed
     * @param source cv, ai_interview or hr; null if there is no location yet
     */
    public record CandidatePlace(String text, String label, String source, boolean found) {
    }

    /**
     * @param nearestCity        the candidate's nearest centre city (null if their location isn't known)
     * @param otherCitiesShown   true when the centres in other cities are included, for an exception
     * @param otherCities        how many other cities have centres
     */
    public record CentreOptions(CandidatePlace candidate, String nearestCity, Integer nearestCityMinutes,
                                Double nearestCityKm, boolean otherCitiesShown, int otherCities, String method,
                                List<CentreView> centres) {
    }

    /** Slots this far ahead are offered: the nearest city's centres are used whichever date they have free. */
    private static final Duration LOOK_AHEAD = Duration.ofDays(60);

    private final Tenancy tenancy;
    private final com.epictalenttrust.hr.candidate.CandidateLocationService locations;
    private final Places places;
    private final CentreRouter router;
    private final JdbcClient jdbc;
    private final Clock clock;

    public CentreOptionsService(Tenancy tenancy, com.epictalenttrust.hr.candidate.CandidateLocationService locations,
                                Places places, CentreRouter router, JdbcClient jdbc, Clock clock) {
        this.tenancy = tenancy;
        this.locations = locations;
        this.places = places;
        this.router = router;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CentreOptions options(UUID applicationId, LocalDate day, String timeOfDay, boolean otherCities) {
        JobApplication application = tenancy.application(applicationId);
        var location = locations.find(application.getCandidateId());
        CandidatePlace where = location
                .map(l -> new CandidatePlace(l.text(), l.label(), l.source().name().toLowerCase(Locale.ROOT), l.placed()))
                .orElse(new CandidatePlace(null, null, null, false));
        GeoPoint from = location.filter(com.epictalenttrust.hr.candidate.CandidateLocationService.Location::placed)
                .map(com.epictalenttrust.hr.candidate.CandidateLocationService.Location::point).orElse(null);
        String fromLabel = location.map(l -> l.place() != null ? l.place() : l.text()).orElse(null);

        // Centres, their free slots and which assessors are free for each slot.
        Instant now = clock.instant();
        Instant start = now.plus(Duration.ofHours(12));
        Instant to = now.plus(LOOK_AHEAD);
        Map<UUID, CentreRow> rows = centres();
        Map<UUID, List<AssessorView>> assessors = assessors();
        Map<UUID, List<Busy>> busy = busyAssessors(start.minus(Duration.ofHours(4)), to.plus(Duration.ofHours(4)));
        Map<UUID, List<CentreRouter.Slot>> slots = new HashMap<>();
        jdbc.sql("""
                        select id, centre_id, start_at, end_at, seats - booked as left_seats from centre_slots
                         where start_at > :from and start_at < :to and seats > booked order by start_at
                        """)
                .param("from", Timestamp.from(start)).param("to", Timestamp.from(to))
                .query((rs, n) -> {
                    UUID centreId = Rows.uuid(rs, "centre_id");
                    Instant slotStart = Rows.instant(rs, "start_at");
                    Instant slotEnd = Rows.instant(rs, "end_at");
                    List<UUID> free = assessors.getOrDefault(centreId, List.of()).stream().map(AssessorView::id)
                            .filter(a -> busy.getOrDefault(a, List.of()).stream().noneMatch(b -> b.overlaps(slotStart, slotEnd)))
                            .toList();
                    slots.computeIfAbsent(centreId, k -> new ArrayList<>())
                            .add(new CentreRouter.Slot(Rows.uuid(rs, "id"), slotStart, slotEnd, rs.getInt("left_seats"), free));
                    return 0;
                })
                .list();
        Map<UUID, Double> load = utilisation(now, now.plus(Duration.ofDays(14)));

        CentreRouter.Route route = router.route(from, fromLabel, routerInput(rows, slots, assessors, load),
                new Preference(day, parse(timeOfDay)), now);
        String nearestKey = route.nearestCity() == null ? null : CentreRouter.cityKey(route.nearestCity());
        long cities = route.centres().stream().map(r -> CentreRouter.cityKey(r.centre().city())).distinct().count();
        List<CentreView> views = route.centres().stream()
                // Without a location there is no nearest city: HR adds the location first, or opens the other cities.
                .filter(r -> otherCities || r.inNearestCity())
                .map(r -> {
                    CentreRow row = rows.get(r.centre().id());
                    List<SlotView> slotViews = r.centre().slots().stream()
                            .map(s -> new SlotView(s.id(), s.startAt(), s.endAt(), s.seatsLeft(), s.freeAssessorIds()))
                            .toList();
                    return new CentreView(row.id(), row.name(), row.address(), row.city(), row.postcode(), row.stepFree(),
                            row.partnerOrg(), r.centre().zone().getId(), r.inNearestCity(), r.recommended(),
                            r.roadKm() == null ? null : Math.round(r.roadKm() * 10) / 10.0, r.travelMinutes(),
                            r.firstSuitableSlot() == null ? null : r.firstSuitableSlot().id(), r.inRotation(), r.turn(),
                            row.lastAssignedAt(), r.reasons(), slotViews, assessors.getOrDefault(row.id(), List.of()));
                })
                .toList();
        return new CentreOptions(where, route.nearestCity(), route.nearestCityMinutes(), route.nearestCityKm(),
                otherCities, (int) Math.max(0, nearestKey == null ? cities : cities - 1),
                "The candidate's location (from the CV, the AI interview or HR) is placed on an offline map of Indian PIN "
                        + "codes, towns and cities. Only the centres in the nearest centre city are offered, whichever "
                        + "dates they have free, and they take turns (round robin) so each gets its share of candidates.",
                views);
    }

    /** The candidate's nearest centre city: how far it is and how many centres it has. */
    public record NearestCity(String name, Integer travelMinutes, Double roadKm, int centres) {
    }

    /** The candidate's nearest centre city, or null if their location isn't known. */
    @Transactional(readOnly = true)
    public NearestCity nearest(UUID candidateId) {
        GeoPoint from = locations.find(candidateId)
                .filter(com.epictalenttrust.hr.candidate.CandidateLocationService.Location::placed)
                .map(com.epictalenttrust.hr.candidate.CandidateLocationService.Location::point).orElse(null);
        if (from == null) {
            return null;
        }
        CentreRouter.Route route = router.route(from, "", routerInput(centres(), Map.of(), Map.of(), Map.of()),
                Preference.NONE, clock.instant());
        if (route.nearestCity() == null) {
            return null;
        }
        return new NearestCity(route.nearestCity(), route.nearestCityMinutes(), route.nearestCityKm(),
                (int) route.centres().stream().filter(CentreRouter.Ranked::inNearestCity).count());
    }

    @Transactional(readOnly = true)
    public String nearestCity(UUID candidateId) {
        NearestCity city = nearest(candidateId);
        return city == null ? null : city.name();
    }

    /**
     * Checks HR's choice of centre. A centre in the candidate's nearest centre city needs nothing more; any other
     * centre needs a short reason, which is returned as the note stored on the interview (and so in the audit trail).
     */
    @Transactional(readOnly = true)
    public String checkCentreChoice(UUID candidateId, UUID centreId, String reason) {
        String city = jdbc.sql("select city from partner_centres where id = :id").param("id", centreId)
                .query(String.class).optional().orElse("");
        String nearest = nearestCity(candidateId);
        if (nearest != null && CentreRouter.cityKey(nearest).equals(CentreRouter.cityKey(city))) {
            return null;
        }
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 5) {
            throw com.epictalenttrust.hr.common.ApiException.badRequest("outside_reason_required", nearest == null
                    ? "The candidate's location isn't known. Add it first, or give a short reason for choosing " + city + "."
                    : city + " isn't the candidate's nearest centre city (" + nearest + "). Give a short reason for choosing it.");
        }
        if (why.length() > 500) {
            why = why.substring(0, 500);
        }
        return (nearest == null ? "Booked in " + city + " without a known location" : "Booked in " + city + " instead of " + nearest)
                + ": " + why;
    }

    private List<CentreRouter.Centre> routerInput(Map<UUID, CentreRow> rows, Map<UUID, List<CentreRouter.Slot>> slots,
                                                 Map<UUID, List<AssessorView>> assessors, Map<UUID, Double> load) {
        List<CentreRouter.Centre> input = new ArrayList<>();
        for (CentreRow row : rows.values()) {
            GeoPoint point = row.latitude() != null && row.longitude() != null
                    ? new GeoPoint(row.latitude(), row.longitude())
                    : places.find(row.city(), row.country()).map(Place::point).orElse(null);
            input.add(new CentreRouter.Centre(row.id(), row.name(), row.city(), point,
                    TimeZones.parseOr(row.timeZone(), TimeZones.INDIA), slots.getOrDefault(row.id(), List.of()),
                    assessors.getOrDefault(row.id(), List.of()).size(), load.getOrDefault(row.id(), 0.0),
                    row.lastTurn(), row.lastAssignedAt()));
        }
        return input;
    }

    /** A candidate was booked at this centre: it goes to the back of the queue of nearby centres. */
    @Transactional
    public void recordTurn(UUID centreId) {
        jdbc.sql("update partner_centres set last_turn = nextval('centre_turn_seq'), last_assigned_at = :at where id = :id")
                .param("at", Timestamp.from(clock.instant())).param("id", centreId).update();
    }

    /** True if the assessor has no other interview overlapping this time. */
    @Transactional(readOnly = true)
    public boolean assessorFree(UUID assessorId, Instant start, Instant end, UUID ignoreInterviewId) {
        return busyAssessors(start.minus(Duration.ofHours(4)), end.plus(Duration.ofHours(4))).getOrDefault(assessorId, List.of())
                .stream().filter(b -> !b.interviewId().equals(ignoreInterviewId)).noneMatch(b -> b.overlaps(start, end));
    }

    // ---------------------------------------------------------------- data

    private record CentreRow(UUID id, String name, String address, String city, String country, String postcode,
                             Boolean stepFree, String partnerOrg, Double latitude, Double longitude, String timeZone,
                             Long lastTurn, Instant lastAssignedAt) {
    }

    private record Busy(UUID interviewId, Instant start, Instant end) {
        boolean overlaps(Instant s, Instant e) {
            return start.isBefore(e) && end.isAfter(s);
        }
    }

    private Map<UUID, CentreRow> centres() {
        Map<UUID, CentreRow> rows = new LinkedHashMap<>();
        jdbc.sql("""
                        select id, name, address, city, country, postcode, step_free_access, partner_org, latitude,
                               longitude, time_zone, last_turn, last_assigned_at
                          from partner_centres
                         where active and (company_id is null or company_id = :company)
                         order by name
                        """)
                .param("company", com.epictalenttrust.hr.security.CurrentUser.companyId())
                .query((rs, n) -> rows.put(Rows.uuid(rs, "id"), new CentreRow(Rows.uuid(rs, "id"), rs.getString("name"),
                        rs.getString("address"), rs.getString("city"), rs.getString("country"), rs.getString("postcode"),
                        (Boolean) rs.getObject("step_free_access"), rs.getString("partner_org"),
                        (Double) rs.getObject("latitude"), (Double) rs.getObject("longitude"), rs.getString("time_zone"),
                        (Long) rs.getObject("last_turn"), Rows.instant(rs, "last_assigned_at"))))
                .list();
        return rows;
    }

    private Map<UUID, List<AssessorView>> assessors() {
        Map<UUID, List<AssessorView>> out = new HashMap<>();
        jdbc.sql("select id, centre_id, full_name from partner_assessors where status = 'active' order by full_name")
                .query((rs, n) -> out.computeIfAbsent(Rows.uuid(rs, "centre_id"), k -> new ArrayList<>())
                        .add(new AssessorView(Rows.uuid(rs, "id"), rs.getString("full_name"))))
                .list();
        return out;
    }

    private Map<UUID, List<Busy>> busyAssessors(Instant from, Instant to) {
        Map<UUID, List<Busy>> out = new HashMap<>();
        jdbc.sql("""
                        select id, assessor_id, start_at, coalesce(duration_min, 60) as minutes from interviews
                         where assessor_id is not null and start_at between :from and :to
                           and status not in ('cancelled', 'no_show')
                        """)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, n) -> {
                    Instant start = Rows.instant(rs, "start_at");
                    return out.computeIfAbsent(Rows.uuid(rs, "assessor_id"), k -> new ArrayList<>())
                            .add(new Busy(Rows.uuid(rs, "id"), start, start.plus(Duration.ofMinutes(rs.getInt("minutes")))));
                })
                .list();
        return out;
    }

    private Map<UUID, Double> utilisation(Instant from, Instant to) {
        Map<UUID, Double> out = new HashMap<>();
        jdbc.sql("""
                        select centre_id, sum(booked)::float / nullif(sum(seats), 0) as used from centre_slots
                         where start_at between :from and :to group by centre_id
                        """)
                .param("from", Timestamp.from(from)).param("to", Timestamp.from(to))
                .query((rs, n) -> out.put(Rows.uuid(rs, "centre_id"), rs.getDouble("used")))
                .list();
        return out;
    }

    private static TimeOfDay parse(String value) {
        if (value == null || value.isBlank()) {
            return TimeOfDay.ANY;
        }
        try {
            return TimeOfDay.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TimeOfDay.ANY;
        }
    }

    static ZoneId zone(String id) {
        return TimeZones.parseOr(id, TimeZones.INDIA);
    }
}
