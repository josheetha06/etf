package com.epictalenttrust.hr.routing;

import com.epictalenttrust.hr.routing.CentreRouter.Centre;
import com.epictalenttrust.hr.routing.CentreRouter.Preference;
import com.epictalenttrust.hr.routing.CentreRouter.Ranked;
import com.epictalenttrust.hr.routing.CentreRouter.Slot;
import com.epictalenttrust.hr.routing.CentreRouter.TimeOfDay;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CentreRouterTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = LocalDate.of(2026, 11, 9).atTime(9, 0).atZone(IST).toInstant();

    private static final int NO_SLOTS = -1;

    private final Places places = new Places();
    private final CentreRouter router = new CentreRouter(new StraightLineTravelEstimator());

    @Test
    void readsLocationsTheWayCvsWriteThem() {
        assertThat(places.find("Anna Nagar, Madurai 625020", "India")).get().extracting(Places.Place::name).isEqualTo("Madurai");
        assertThat(places.find("Bangalore", null)).get().extracting(Places.Place::name).isEqualTo("Bengaluru");
        assertThat(places.find("Trichy", "India")).get().extracting(Places.Place::name).isEqualTo("Tiruchirappalli");
        assertThat(places.find("Leeds, United Kingdom", null)).get().extracting(Places.Place::country).isEqualTo("United Kingdom");
        assertThat(places.find("Somewhere unknown", "India")).isEmpty();
    }

    @Test
    void distancesAreRealistic() {
        GeoPoint madurai = places.find("Madurai", null).orElseThrow().point();
        GeoPoint chennai = places.find("Chennai", null).orElseThrow().point();
        assertThat(madurai.kmTo(chennai)).isBetween(410.0, 440.0);
    }

    @Test
    void placesSmallTownsByPinCodeTownOrDistrict() {
        Places.Located sivakasi = places.locate("Sivakasi 626123", "India").orElseThrow();
        assertThat(sivakasi.matchedBy()).isEqualTo("pin");
        assertThat(sivakasi.district()).isEqualTo("Virudhunagar");
        assertThat(sivakasi.label()).isEqualTo("Sivakasi, Virudhunagar, Tamil Nadu 626123");
        assertThat(places.locate("Rajapalayam, Tamil Nadu", null)).get()
                .extracting(Places.Located::matchedBy).isEqualTo("town");
        assertThat(places.locate("Virudhunagar District", null)).get()
                .extracting(Places.Located::name).isEqualTo("Virudhunagar");
        // A known city in the text wins over a town name used in several places.
        assertThat(places.locate("Anna Nagar, Madurai", "India")).get().extracting(Places.Located::name).isEqualTo("Madurai");
        assertThat(places.locate("Nowhere at all", "India")).isEmpty();
    }

    @Test
    void onlyTheNearestCitysCentresAreOffered() {
        GeoPoint from = places.locate("Sivakasi 626123", "India").orElseThrow().point();
        CentreRouter.Route route = router.route(from, "Sivakasi", centres(), Preference.NONE, NOW);
        assertThat(route.nearestCity()).isEqualTo("Madurai");
        List<Ranked> nearest = route.centres().stream().filter(Ranked::inNearestCity).toList();
        assertThat(nearest).hasSize(3).allSatisfy(r -> assertThat(r.centre().city()).isEqualTo("Madurai"));
        assertThat(nearest.getFirst().recommended()).isTrue();
        // Other cities come after, nearest first, for exceptions.
        assertThat(route.centres().subList(3, route.centres().size())).extracting(r -> r.centre().city())
                .containsExactly("Tiruchirappalli", "Chennai");
    }

    @Test
    void theNearestCityIsUsedWhateverDateItHasFree() {
        // Every Madurai centre is booked up for three weeks; Trichy has a slot tomorrow. Madurai is still offered.
        UUID assessor = UUID.randomUUID();
        List<Centre> centres = List.of(
                centre("Anna Nagar", "Madurai", 9.9259, 78.1453, 21, assessor, 0.0),
                centre("IT Park", "Madurai", 9.9440, 78.1600, 25, assessor, 0.0),
                centre("Trichy", "Tiruchirappalli", 10.8250, 78.6830, 1, assessor, 0.0));
        GeoPoint from = places.find("Madurai", "India").orElseThrow().point();
        CentreRouter.Route route = router.route(from, "Madurai", centres, Preference.NONE, NOW);
        assertThat(route.centres().getFirst().centre().name()).isEqualTo("Anna Nagar");
        assertThat(route.centres().getFirst().recommended()).isTrue();
        assertThat(route.centres().getFirst().firstSuitableSlot().startAt())
                .isAfter(NOW.plus(Duration.ofDays(20)));
    }

    @Test
    void theCitysCentresTakeTurns() {
        GeoPoint from = places.find("Anna Nagar, Madurai", "India").orElseThrow().point();
        Map<String, Long> turns = new HashMap<>();
        Map<String, Integer> sent = new HashMap<>();
        long turn = 0;
        for (int candidate = 0; candidate < 6; candidate++) {
            List<Ranked> ranked = router.route(from, "Madurai", madurai(turns, 1, 1, 1), Preference.NONE, NOW).centres();
            Ranked next = ranked.getFirst();
            assertThat(next.recommended()).isTrue();
            assertThat(next.turn()).isEqualTo(1);
            turns.put(next.centre().name(), ++turn);
            sent.merge(next.centre().name(), 1, Integer::sum);
        }
        // Six candidates, three Madurai centres: two each, not six for the nearest.
        assertThat(sent).containsOnly(Map.entry("Anna Nagar", 2), Map.entry("K K Nagar", 2), Map.entry("IT Park", 2));
    }

    @Test
    void theCentreThatWaitedLongestGoesNextEvenWithALaterDate() {
        GeoPoint from = places.find("Anna Nagar, Madurai", "India").orElseThrow().point();
        Map<String, Long> turns = Map.of("Anna Nagar", 3L, "K K Nagar", 2L, "IT Park", 1L);
        // IT Park's first free day is a week later than the others, but it's its turn.
        List<Ranked> ranked = router.route(from, "Madurai", madurai(turns, 1, 1, 8), Preference.NONE, NOW).centres();
        assertThat(ranked).extracting(r -> r.centre().name()).startsWith("IT Park", "K K Nagar", "Anna Nagar");
        assertThat(ranked).extracting(Ranked::turn).startsWith(1, 2, 3);
        assertThat(ranked.getFirst().reasons()).anyMatch(r -> r.startsWith("Next in turn of the 3 Madurai centres"));
        // Other cities are not part of the rotation.
        assertThat(ranked.getLast().inRotation()).isFalse();
        assertThat(ranked.getLast().inNearestCity()).isFalse();
    }

    @Test
    void aCentreWithNoFreeSlotSitsOutAndKeepsItsPlace() {
        GeoPoint from = places.find("Madurai", "India").orElseThrow().point();
        Map<String, Long> turns = Map.of("Anna Nagar", 3L, "K K Nagar", 2L);
        // IT Park waited longest but has no free slot at all, so K K Nagar goes this time ...
        List<Ranked> ranked = router.route(from, "Madurai", madurai(turns, 1, 1, NO_SLOTS), Preference.NONE, NOW).centres();
        assertThat(ranked.getFirst().centre().name()).isEqualTo("K K Nagar");
        Ranked itPark = ranked.stream().filter(r -> r.centre().name().equals("IT Park")).findFirst().orElseThrow();
        assertThat(itPark.turn()).isNull();
        assertThat(itPark.reasons()).anyMatch(r -> r.startsWith("Sits out this turn"));
        // ... and once it has room again it is first in line.
        ranked = router.route(from, "Madurai", madurai(Map.of("Anna Nagar", 3L, "K K Nagar", 4L), 1, 1, 2),
                Preference.NONE, NOW).centres();
        assertThat(ranked.getFirst().centre().name()).isEqualTo("IT Park");
    }

    @Test
    void preferredDayAndTimeAreRespected() {
        GeoPoint from = places.find("Madurai", "India").orElseThrow().point();
        LocalDate day = LocalDate.of(2026, 11, 12);
        List<Ranked> ranked = router.route(from, "Madurai", centres(), new Preference(day, TimeOfDay.AFTERNOON), NOW).centres();
        Slot slot = ranked.getFirst().firstSuitableSlot();
        assertThat(slot.startAt().atZone(IST).toLocalDate()).isEqualTo(day);
        assertThat(slot.startAt().atZone(IST).toLocalTime()).isAfterOrEqualTo(LocalTime.NOON);
    }

    @Test
    void unknownLocationHasNoNearestCity() {
        CentreRouter.Route route = router.route(null, null, centres(), Preference.NONE, NOW);
        assertThat(route.nearestCity()).isNull();
        assertThat(route.centres()).hasSize(centres().size()).noneMatch(Ranked::inNearestCity);
    }

    /** Three Madurai centres a few minutes apart, plus Trichy; first free day for each Madurai centre. */
    private static List<Centre> madurai(Map<String, Long> turns, int annaNagar, int kkNagar, int itPark) {
        UUID assessor = UUID.randomUUID();
        return List.of(
                centre("Anna Nagar", "Madurai", 9.9259, 78.1453, annaNagar, assessor, 0.0, turns.get("Anna Nagar")),
                centre("K K Nagar", "Madurai", 9.9348, 78.1440, kkNagar, assessor, 0.0, turns.get("K K Nagar")),
                centre("IT Park", "Madurai", 9.9440, 78.1600, itPark, assessor, 0.0, turns.get("IT Park")),
                centre("Trichy", "Tiruchirappalli", 10.8250, 78.6830, 1, assessor, 0.0, null));
    }

    private List<Centre> centres() {
        UUID assessor = UUID.randomUUID();
        return List.of(
                centre("Madurai Anna Nagar", "Madurai", 9.9259, 78.1453, 1, assessor, 0.1),
                centre("Madurai busy", "Madurai", 9.9348, 78.1440, 7, assessor, 0.9),
                centre("Madurai IT Park", "Madurai", 9.9440, 78.1600, 2, assessor, 0.0),
                centre("Trichy", "Tiruchirappalli", 10.8250, 78.6830, 1, assessor, 0.0),
                centre("Chennai", "Chennai", 13.0108, 80.2155, 1, assessor, 0.0));
    }

    private static Centre centre(String name, String city, double lat, double lon, int firstDay, UUID assessor, double load) {
        return centre(name, city, lat, lon, firstDay, assessor, load, null);
    }

    private static Centre centre(String name, String city, double lat, double lon, int firstDay, UUID assessor, double load,
                                 Long lastTurn) {
        List<Slot> slots = new java.util.ArrayList<>();
        for (int d = firstDay; firstDay != NO_SLOTS && d < firstDay + 7; d++) {
            for (LocalTime t : List.of(LocalTime.of(10, 0), LocalTime.of(15, 0))) {
                Instant start = LocalDate.of(2026, 11, 9).plusDays(d).atTime(t).atZone(IST).toInstant();
                slots.add(new Slot(UUID.randomUUID(), start, start.plus(Duration.ofHours(1)), 2, List.of(assessor)));
            }
        }
        return new Centre(UUID.randomUUID(), name, city, new GeoPoint(lat, lon), IST, slots, 1, load, lastTurn,
                lastTurn == null ? null : NOW.minus(Duration.ofDays(10 - lastTurn)));
    }
}
