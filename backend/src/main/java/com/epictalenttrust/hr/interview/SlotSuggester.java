package com.epictalenttrust.hr.interview;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suggests interview times that fall inside working hours for both HR and the candidate,
 * on weekdays, without clashing with HR's other interviews. HR always confirms the time.
 */
public final class SlotSuggester {

    public record Interval(Instant start, Instant end) {
        boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }

    public record CandidateZone(UUID applicationId, ZoneId zone) {
    }

    private static final LocalTime HR_DAY_START = LocalTime.of(9, 0);
    private static final LocalTime HR_DAY_END = LocalTime.of(18, 0);
    private static final LocalTime CANDIDATE_DAY_START = LocalTime.of(9, 30);
    private static final LocalTime CANDIDATE_DAY_END = LocalTime.of(19, 0);
    private static final Duration STEP = Duration.ofMinutes(30);
    private static final Duration BUFFER = Duration.ofMinutes(15);
    private static final int SEARCH_DAYS = 21;

    private SlotSuggester() {
    }

    /** One suggestion per candidate, in order, starting from the next working day. */
    public static Map<UUID, Instant> suggest(List<CandidateZone> candidates, ZoneId hrZone, Instant now,
                                             Duration length, List<Interval> busy) {
        List<Interval> taken = new ArrayList<>(busy);
        Map<UUID, Instant> result = new LinkedHashMap<>();
        LocalDate firstDay = now.atZone(hrZone).toLocalDate().plusDays(1);
        for (CandidateZone candidate : candidates) {
            Instant slot = firstFree(candidate.zone(), hrZone, firstDay, length, taken);
            if (slot != null) {
                result.put(candidate.applicationId(), slot);
                taken.add(new Interval(slot, slot.plus(length)));
            }
        }
        return result;
    }

    /** Up to {@code max} free times on the days a candidate said they are free. */
    public static List<Instant> onDays(ZoneId candidateZone, List<LocalDate> days, TimeOfDay timeOfDay, ZoneId hrZone,
                                       Instant now, Duration length, List<Interval> busy, int max) {
        List<Instant> result = new ArrayList<>();
        LocalTime[] window = window(timeOfDay);
        for (LocalDate day : days) {
            for (ZonedDateTime local = day.atTime(window[0]).atZone(candidateZone);
                 !local.toLocalTime().plus(length).isAfter(window[1]) && local.toLocalDate().equals(day);
                 local = local.plus(STEP)) {
                Instant start = local.toInstant();
                if (start.isAfter(now.plus(Duration.ofHours(2))) && fitsHr(start, length, hrZone) && isFree(start, length, busy)) {
                    result.add(start);
                    if (result.size() >= max) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private static Instant firstFree(ZoneId candidateZone, ZoneId hrZone, LocalDate firstDay, Duration length,
                                     List<Interval> taken) {
        for (int offset = 0; offset < SEARCH_DAYS; offset++) {
            LocalDate day = firstDay.plusDays(offset);
            if (isWeekend(day)) {
                continue;
            }
            for (ZonedDateTime hrLocal = day.atTime(HR_DAY_START).atZone(hrZone);
                 !hrLocal.toLocalTime().plus(length).isAfter(HR_DAY_END);
                 hrLocal = hrLocal.plus(STEP)) {
                Instant start = hrLocal.toInstant();
                if (fitsCandidate(start, length, candidateZone) && isFree(start, length, taken)) {
                    return start;
                }
            }
        }
        return null;
    }

    private static boolean fitsCandidate(Instant start, Duration length, ZoneId zone) {
        ZonedDateTime local = start.atZone(zone);
        LocalTime begin = local.toLocalTime();
        LocalTime end = begin.plus(length);
        return !isWeekend(local.toLocalDate()) && !begin.isBefore(CANDIDATE_DAY_START)
                && !end.isAfter(CANDIDATE_DAY_END) && end.isAfter(begin);
    }

    private static boolean fitsHr(Instant start, Duration length, ZoneId hrZone) {
        ZonedDateTime local = start.atZone(hrZone);
        LocalTime begin = local.toLocalTime();
        LocalTime end = begin.plus(length);
        return !isWeekend(local.toLocalDate()) && !begin.isBefore(HR_DAY_START) && !end.isAfter(HR_DAY_END)
                && end.isAfter(begin);
    }

    private static boolean isFree(Instant start, Duration length, List<Interval> taken) {
        Instant end = start.plus(length);
        return taken.stream().noneMatch(t -> t.overlaps(start.minus(BUFFER), end.plus(BUFFER)));
    }

    private static boolean isWeekend(LocalDate day) {
        return day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static LocalTime[] window(TimeOfDay timeOfDay) {
        return switch (timeOfDay == null ? TimeOfDay.ANY : timeOfDay) {
            case MORNING -> new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(12, 0)};
            case AFTERNOON -> new LocalTime[]{LocalTime.of(12, 0), LocalTime.of(17, 0)};
            case EVENING -> new LocalTime[]{LocalTime.of(17, 0), LocalTime.of(20, 0)};
            case ANY -> new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(19, 0)};
        };
    }
}
