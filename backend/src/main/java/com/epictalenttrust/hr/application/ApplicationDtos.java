package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.job.CriterionKind;
import com.epictalenttrust.hr.screening.AiSuggestion;
import com.epictalenttrust.hr.screening.EvidenceStrength;
import com.epictalenttrust.hr.shortlist.ShortlistDecisionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    /** One row of a job's CV list and AI shortlist. */
    public record ApplicationRow(UUID id, UUID candidateId, String candidateRef, String candidateName, String city,
                                 String country, CvStatus cvStatus, ApplicationSource source, Instant addedAt,
                                 Integer matchScore, AiSuggestion suggestion, int mustHaveMatched, int mustHaveTotal,
                                 Integer experienceYears, String certificationsFound, ShortlistDecisionType decision,
                                 Instant decidedAt, ApplicationStage stage, ApplicationStatus status,
                                 int mustHaveBacked, String summary, String reason, int redFlags, int redFlagsOpen,
                                 int checkFlags, String sourceDetail, Integer criteriaVersion, String nextAction,
                                 com.epictalenttrust.hr.candidate.LocationSummary location) {
    }

    /**
     * How one criterion was judged: the strength of the evidence (recent job, older job, skills list, mention only),
     * the points it earned and the CV line it came from.
     */
    public record EvidenceView(UUID id, UUID criterionId, CriterionKind kind, String value, boolean matched,
                               String quote, boolean flagged, String flagNote, String flaggedByName,
                               EvidenceStrength strength, String strengthLabel, Double points, String cvSection,
                               String matchedTerm, Integer minYears) {
    }

    /** A thing in the CV that HR should look at. Flags never change the score. */
    public record FlagView(UUID id, String severity, String kind, String message, String quote, String reviewOutcome,
                           String reviewNote, String reviewedByName, Instant reviewedAt) {
    }

    public record ScreeningView(UUID id, String modelVersion, Instant screenedAt, int matchScore, int mustHavePts,
                                int niceToHavePts, int experiencePts, int certificationPts, Integer experienceYears,
                                String certificationsFound, AiSuggestion suggestion, List<EvidenceView> evidence,
                                Integer criteriaVersion, Double totalYears, Double relevantYears, String summary,
                                String reason, String readingQuality, Integer shortlistCutoff, Integer reserveCutoff,
                                List<FlagView> flags) {
    }

    public record DecisionView(ShortlistDecisionType decision, AiSuggestion aiSuggestion, String decidedByName,
                               Instant decidedAt, String reason) {
    }
}
