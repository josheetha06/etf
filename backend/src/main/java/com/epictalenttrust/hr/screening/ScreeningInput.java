package com.epictalenttrust.hr.screening;

import java.time.YearMonth;
import java.util.List;

/**
 * Everything the engine needs for one CV. The text has already had names, contact details and
 * personal data removed (see {@link CvRedactor}).
 */
public record ScreeningInput(
        String redactedText,
        List<ScreeningCriterion> criteria,
        Integer minExperienceYears,
        int shortlistCutoff,
        int reserveCutoff,
        SkillDictionary.Vocabulary vocabulary,
        YearMonth now) {
}
