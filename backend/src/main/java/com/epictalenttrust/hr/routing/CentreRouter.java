package com.epictalenttrust.hr.routing;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Suggests partner assessment centres for a candidate.
 *
 * <ol>
 *   <li><b>Where the candidate is</b>: their location (from the CV, the AI interview or HR) placed on the map with
 *       {@link Places}.</li>
 *   <li><b>The nearest centre city</b>: haversine distance turned into road distance and journey time by the
 *       {@link TravelEstimator}. The city with the closest centre is the candidate's centre city, for example Madurai
 *       for a candidate in Virudhunagar or Sivakasi. Only that city's centres are offered, whichever dates they have
 *       free: a later date in the nearest city is better for the candidate than a long journey.</li>
 *   <li><b>Taking turns (round robin)</b>: the centres in that city share candidates in turn rather than the
 *       nearest one getting everyone. Each booking stamps the centre with the next turn number, and the centre that
 *       has gone longest without a candidate is suggested next. A centre with no free slot with an assessor sits out
 *       that turn and keeps its place in the queue. If HR picks a different centre, the one that was next stays
 *       first in line.</li>
 *   <li><b>Other cities</b> are listed after the nearest city, nearest first, for exceptions (HR gives a reason
 *       when booking one).</li>
 * </ol>
 * Every centre carries plain-English reasons, so HR can see why it is first and still choose another.
 */
@Component
public class CentreRouter {

    /** Centres within this journey time are "within reach". */
    public static final int REACH_MINUTES = 120;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    public enum TimeOfDay { ANY, MORNING, AFTERNOON }

    /** A slot with the assessors who are free for it. */
    public record Slot(UUID id, Instant startAt, Instant endAt, int seatsLeft, List<UUID> freeAssessorIds) {
    }

    /**
     * @param lastTurn       turn number of the last candidate sent here (higher is more recent), or null if none yet
     * @param lastAssignedAt when that candidate was booked
     */
    public record Centre(UUID id, String name, String city, GeoPoint point, ZoneId zone, List<Slot> slots,
                         int assessors, double utilisation, Long lastTurn, Instant lastAssignedAt) {
    }

    public record Preference(LocalDate day, TimeOfDay timeOfDay) {
        public static final Preference NONE = new Preference(null, TimeOfDay.ANY);
    }

    /**
     * @param inNearestCity true if the centre is in the candidate's nearest centre city
     * @param inRotation    true if it is one of several centres in that city that take turns
     * @param turn          its place in the queue (1 = next), or null if it sits out this turn or isn't in the rotation
     */
    public record Ranked(Centre centre, Double straightKm, Double roadKm, Integer travelMinutes, boolean withinReach,
                         boolean recommended, Slot firstSuitableSlot, boolean inNearestCity, boolean inRotation,
                         Integer turn, List<String> reasons) {

        Ranked with(boolean recommended, boolean inNearestCity, boolean inRotation, Integer turn) {
            return new Ranked(centre, straightKm, roadKm, travelMinutes, withinReach, recommended, firstSuitableSlot,
                    inNearestCity, inRotation, turn, reasons);
        }
    }

    /** The candidate's nearest centre city and its centres in turn order, then every other centre, nearest first. */
    public record Route(String nearestCity, Integer nearestCityMinutes, Double nearestCityKm, List<Ranked> centres) {
    }

    private final TravelEstimator travel;

    public CentreRouter(TravelEstimator travel) {
        this.travel = travel;
    }

    /**
     * @param from      where the candidate is, or null if their location isn't known
     * @param fromLabel the place's name, for the reasons ("from Sivakasi")
     */
    public Route route(GeoPoint from, String fromLabel, List<Centre> centres, Preference preference, Instant now) {
        List<Ranked> all = new ArrayList<>();
        for (Centre centre : centres) {
            List<String> reasons = new ArrayList<>();
            Double straight = null;
            Double road = null;
            Integer minutes = null;
            if (from != null && centre.point() != null) {
                TravelEstimator.Travel t = travel.estimate(from, centre.point());
                straight = t.straightKm();
                road = t.roadKm();
                minutes = t.minutes();
                reasons.add(distanceText(road, minutes, fromLabel));
            }
            Slot slot = centre.slots().stream()
                    .filter(s -> s.seatsLeft() > 0 && !s.freeAssessorIds().isEmpty())
                    .filter(s -> matches(s, centre.zone(), preference))
                    .min(Comparator.comparing(Slot::startAt))
                    .orElse(null);
            if (slot != null) {
                reasons.add("First free slot " + WHEN.format(slot.startAt().atZone(centre.zone()))
                        + " (" + slot.freeAssessorIds().size() + " of " + centre.assessors() + " assessors free)");
            } else {
                reasons.add(preference.day() != null || preference.timeOfDay() != TimeOfDay.ANY
                        ? "No free slot with an assessor for the day or time you chose"
                        : "No free slot with an assessor in the next two months");
            }
            boolean reach = minutes == null || minutes <= REACH_MINUTES;
            all.add(new Ranked(centre, straight, road, minutes, reach, false, slot, false, false, null, reasons));
        }

        // Nearest first (unknown distances last), then by name, so other cities come out nearest first too.
        all.sort(Comparator.comparing((Ranked r) -> r.travelMinutes() == null ? Integer.MAX_VALUE : r.travelMinutes())
                .thenComparing(r -> cityKey(r.centre().city()))
                .thenComparing(r -> r.centre().name()));
        Ranked nearest = all.stream().filter(r -> r.travelMinutes() != null).findFirst().orElse(null);
        if (nearest == null) {
            return new Route(null, null, null, groupedByCity(all));
        }
        String city = cityKey(nearest.centre().city());
        List<Ranked> inCity = all.stream().filter(r -> cityKey(r.centre().city()).equals(city)).toList();
        List<Ranked> others = all.stream().filter(r -> !cityKey(r.centre().city()).equals(city)).toList();

        List<Ranked> out = new ArrayList<>(takeTurns(inCity, nearest.centre().city(), now));
        out.addAll(groupedByCity(others));
        return new Route(nearest.centre().city(), nearest.travelMinutes(),
                nearest.roadKm() == null ? null : Math.round(nearest.roadKm() * 10) / 10.0, out);
    }

    /**
     * Round robin between the nearest city's centres: the one that has gone longest without a candidate goes first,
     * whatever its first free date. A centre with no free slot sits out and keeps its place.
     */
    private static List<Ranked> takeTurns(List<Ranked> inCity, String cityName, Instant now) {
        List<Ranked> queue = inCity.stream()
                .filter(r -> r.firstSuitableSlot() != null)
                .sorted(Comparator.comparing((Ranked r) -> r.centre().lastTurn(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(r -> r.centre().name()))
                .toList();
        boolean sharing = inCity.size() > 1;
        List<Ranked> out = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            Ranked r = queue.get(i);
            if (sharing) {
                String place = i == 0 ? "Next in turn" : ordinal(i + 1) + " in turn";
                r.reasons().add(0, place + " of the " + inCity.size() + " " + cityName + " centres, which take turns. "
                        + lastSent(r.centre().lastAssignedAt(), r.centre().zone(), now));
            } else if (i == 0) {
                r.reasons().add(0, "The only partner assessment centre in " + cityName);
            }
            out.add(r.with(i == 0, true, sharing, sharing ? i + 1 : null));
        }
        for (Ranked r : inCity) {
            if (r.firstSuitableSlot() == null) {
                r.reasons().add(0, sharing
                        ? "Sits out this turn (no free slot with an assessor) and keeps its place in the queue"
                        : "No free slot at the moment. Use \"Show nearest cities\" if the candidate can't wait");
                out.add(r.with(false, true, sharing, null));
            }
        }
        return out;
    }

    /** Keeps each city's centres together, cities nearest first. */
    private static List<Ranked> groupedByCity(List<Ranked> sorted) {
        Map<String, Integer> cityOrder = new HashMap<>();
        for (Ranked r : sorted) {
            cityOrder.putIfAbsent(cityKey(r.centre().city()), cityOrder.size());
        }
        return sorted.stream()
                .sorted(Comparator.comparing((Ranked r) -> cityOrder.get(cityKey(r.centre().city()))))
                .toList();
    }

    static String cityKey(String city) {
        return city == null ? "" : Places.key(city);
    }

    private static String lastSent(Instant last, ZoneId zone, Instant now) {
        if (last == null) {
            return "No candidate sent here yet";
        }
        long days = Duration.between(last, now).toDays();
        String when = days <= 0 ? "today" : days == 1 ? "yesterday" : days < 14 ? days + " days ago"
                : "on " + DAY.format(last.atZone(zone));
        return "Last candidate sent here " + when;
    }

    private static String ordinal(int n) {
        return n + switch (n % 100 >= 11 && n % 100 <= 13 ? 0 : n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    static boolean matches(Slot slot, ZoneId zone, Preference preference) {
        var local = slot.startAt().atZone(zone);
        if (preference.day() != null && !local.toLocalDate().equals(preference.day())) {
            return false;
        }
        LocalTime time = local.toLocalTime();
        return switch (preference.timeOfDay()) {
            case MORNING -> time.isBefore(LocalTime.NOON);
            case AFTERNOON -> !time.isBefore(LocalTime.NOON);
            case ANY -> true;
        };
    }

    private static String distanceText(double roadKm, int minutes, String fromLabel) {
        String km = roadKm < 10 ? String.format(Locale.ENGLISH, "%.1f", roadKm) : String.valueOf(Math.round(roadKm));
        String time = minutes < 60 ? minutes + " min" : (minutes / 60) + " h " + (minutes % 60) + " min";
        return "About " + km + " km by road from " + fromLabel + " (" + time + ")";
    }
}
