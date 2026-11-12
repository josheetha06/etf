package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.job.CriterionKind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The score for one CV, how it adds up, and the CV lines behind every part of it. */
public record ScreeningOutcome(
        int matchScore,
        int mustHavePts,
        int niceToHavePts,
        int experiencePts,
        int certificationPts,
        double totalYears,
        double relevantYears,
        int statedYears,
        String certificationsFound,
        AiSuggestion suggestion,
        boolean allMustHavesMet,
        String reason,
        String summary,
        boolean readable,
        List<CriterionResult> evidence,
        Map<String, Integer> termCounts,
        CvStructure structure) {

    /** Years rounded down, for the original experience_years column. */
    public Integer experienceYears() {
        return (int) Math.floor(totalYears);
    }

    public record CriterionResult(
            UUID criterionId,
            CriterionKind kind,
            String value,
            boolean matched,
            String quote,
            EvidenceStrength strength,
            double points,
            String section,
            String matchedTerm,
            Double skillYears) {
    }
}
