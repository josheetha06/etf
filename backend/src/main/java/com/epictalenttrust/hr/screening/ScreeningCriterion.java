package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.job.CriterionKind;

import java.util.UUID;

/** One thing the job asks for. {@code minYears} is optional, for must-haves only. */
public record ScreeningCriterion(UUID id, CriterionKind kind, String value, Integer minYears) {

    public ScreeningCriterion(UUID id, CriterionKind kind, String value) {
        this(id, kind, value, null);
    }
}
