package com.epictalenttrust.hr.interview;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SlotSuggesterTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    private static final Instant FRIDAY_NOON_IST = ZonedDateTime.of(2026, 11, 6, 12, 0, 0, 0, IST).toInstant();
    private static final Duration HOUR = Duration.ofMinutes(45);

    @Test
    void suggestsWeekdayTimesInsideBothWorkingDays() {
        UUID india = UUID.randomUUID();
        UUID uk = UUID.randomUUID();
        Map<UUID, Instant> slots = SlotSuggester.suggest(
                List.of(new SlotSuggester.CandidateZone(india, IST), new SlotSuggester.CandidateZone(uk, LONDON)),
                IST, FRIDAY_NOON_IST, HOUR, new ArrayList<>());

        ZonedDateTime indiaLocal = slots.get(india).atZone(IST);
        assertThat(indiaLocal.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(indiaLocal.toLocalTime()).isAfterOrEqualTo(LocalTime.of(9, 30));

        ZonedDateTime ukLocal = slots.get(uk).atZone(LONDON);
        assertThat(ukLocal.toLocalTime()).isAfterOrEqualTo(LocalTime.of(9, 30));
        assertThat(slots.get(uk).atZone(IST).toLocalTime().plus(HOUR)).isBeforeOrEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void neverOverlapsExistingInterviews() {
        Instant monday10 = ZonedDateTime.of(2026, 11, 9, 10, 0, 0, 0, IST).toInstant();
        List<SlotSuggester.Interval> busy = new ArrayList<>(List.of(new SlotSuggester.Interval(monday10, monday10.plus(Duration.ofHours(3)))));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        Map<UUID, Instant> slots = SlotSuggester.suggest(
                List.of(new SlotSuggester.CandidateZone(a, IST), new SlotSuggester.CandidateZone(b, IST)), IST, FRIDAY_NOON_IST, HOUR, busy);
        for (Instant slot : slots.values()) {
            assertThat(slot.plus(HOUR).isBefore(monday10) || slot.isAfter(monday10.plus(Duration.ofHours(3)))).isTrue();
        }
        assertThat(Duration.between(slots.get(a), slots.get(b)).abs()).isGreaterThanOrEqualTo(HOUR);
    }

    @Test
    void offersTimesOnlyOnTheDaysTheCandidateGave() {
        LocalDate tuesday = LocalDate.of(2026, 11, 10);
        List<Instant> options = SlotSuggester.onDays(IST, List.of(tuesday), TimeOfDay.MORNING, IST, FRIDAY_NOON_IST,
                HOUR, List.of(), 6);
        assertThat(options).isNotEmpty().allSatisfy(slot -> {
            ZonedDateTime local = slot.atZone(IST);
            assertThat(local.toLocalDate()).isEqualTo(tuesday);
            assertThat(local.toLocalTime()).isBefore(LocalTime.of(12, 0));
        });
    }
}
