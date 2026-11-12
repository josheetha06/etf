package com.epictalenttrust.hr.interview;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyDeadlineTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");

    @Test
    void defaultsToSixPmTwoDaysAhead() {
        assertThat(SchedulingService.defaultReplyBy(NOW, IST, null)).isEqualTo(Instant.parse("2026-11-05T12:30:00Z"));
    }

    @Test
    void leavesTwelveHoursBeforeAnEarlyInterview() {
        Instant interview = Instant.parse("2026-11-05T06:00:00Z");
        assertThat(SchedulingService.defaultReplyBy(NOW, IST, interview)).isEqualTo(Instant.parse("2026-11-04T18:00:00Z"));
    }

    @Test
    void neverFallsInsideTheNextTwoHours() {
        Instant interview = Instant.parse("2026-11-03T12:00:00Z");
        assertThat(SchedulingService.defaultReplyBy(NOW, IST, interview)).isEqualTo(NOW.plusSeconds(7200));
    }
}
