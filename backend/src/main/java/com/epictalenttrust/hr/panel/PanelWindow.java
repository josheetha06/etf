package com.epictalenttrust.hr.panel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * When a panel interview's code works: from an hour before the interview until a set time after it ends (48 hours
 * by default, or until HR decides). The sample data uses a longer window so demo codes keep working for two weeks.
 */
@Component
public class PanelWindow {

    public static final Duration OPENS_BEFORE = Duration.ofHours(1);

    private final Duration closesAfter;

    public PanelWindow(@Value("${ett.panel.review-window-hours:48}") long hours) {
        this.closesAfter = Duration.ofHours(Math.max(1, hours));
    }

    public Instant opensAt(Instant start) {
        return start.minus(OPENS_BEFORE);
    }

    public Instant closesAt(Instant start, Integer durationMin) {
        return start.plus(Duration.ofMinutes(durationMin == null ? 60 : durationMin)).plus(closesAfter);
    }

    public long closesAfterHours() {
        return closesAfter.toHours();
    }
}
