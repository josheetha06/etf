package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.common.PgEnumConverter;
import jakarta.persistence.Converter;

/** How strongly the CV shows a skill, from used in a recent job down to not found. */
public enum EvidenceStrength {
    RECENT_ROLE(1.0, "Used in a job in the last 3 years"),
    OLDER_ROLE(0.75, "Used in an older job or project"),
    SKILLS_ONLY(0.5, "Only listed as a skill"),
    MENTION_ONLY(0.25, "Only mentioned (training, hobbies or basic knowledge)"),
    NEGATED(0, "The CV says they don't have it"),
    NOT_FOUND(0, "Not found in the CV");

    @Converter(autoApply = true)
    public static class DbConverter extends PgEnumConverter<EvidenceStrength> {
        public DbConverter() {
            super(EvidenceStrength.class);
        }
    }

    private final double credit;
    private final String label;

    EvidenceStrength(double credit, String label) {
        this.credit = credit;
        this.label = label;
    }

    public double credit() {
        return credit;
    }

    public String label() {
        return label;
    }

    /** Backed by real work: a job or a project. */
    public boolean backedByWork() {
        return this == RECENT_ROLE || this == OLDER_ROLE;
    }
}
