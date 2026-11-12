package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** Where a candidate is. Each job has its own rounds (up to 8); ROUNDn is the job's nth round. */
public enum ApplicationStage {
    SCREENING, SHORTLIST, ROUND1, ROUND2, ROUND3, ROUND4, ROUND5, ROUND6, ROUND7, ROUND8, OFFER, CLOSED;

    public static final int MAX_ROUNDS = 8;

    public static ApplicationStage forRound(int round) {
        if (round < 1 || round > MAX_ROUNDS) {
            throw new IllegalArgumentException("Rounds go from 1 to " + MAX_ROUNDS);
        }
        return valueOf("ROUND" + round);
    }

    /** The round this stage is (1 to 8), or 0 if it isn't a round. */
    public int roundNumber() {
        return name().startsWith("ROUND") ? Integer.parseInt(name().substring(5)) : 0;
    }

    public boolean isRound() {
        return roundNumber() > 0;
    }

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<ApplicationStage> {
        public DbConverter() {
            super(ApplicationStage.class);
        }
    }
}
