package com.epictalenttrust.hr.report;

import com.epictalenttrust.hr.application.ApplicationStage;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record CompetencyView(String competency, Integer aiDraftScore, Integer finalScore, String reviewerComment) {
    }

    public record ReportView(UUID id, UUID interviewId, Instant interviewAt, String reviewerName, Integer aiOverall,
                             Integer finalOverall, Recommendation recommendation, String taskSummary,
                             IntegrityStatus integrityStatus, String reviewerNotes, Instant releasedAt,
                             List<CompetencyView> competencies, String identityCheck, String identityNote,
                             String surroundingsCheck, String surroundingsNote, List<String> highlights, boolean sample,
                             Instant recheckRequestedAt, String recheckNote, String status,
                             String candidateLocation, String candidatePin, Integer candidateNoticeDays,
                             Double candidateExpectedCtcLpa) {
    }

    /** Round 2: the partner assessor's report. */
    public record Round2View(UUID interviewId, Instant interviewAt, String centreName, String assessorName, Integer score,
                             Recommendation recommendation, Boolean idChecked, String idCheckNote,
                             java.util.Map<String, Integer> areaScores, String notes, Instant submittedAt, boolean sample) {
    }

    public record StageDecisionView(UUID id, ApplicationStage stage, StageDecisionType decision, String reason,
                                    String decidedByName, Instant decidedAt, Instant challengedAt, Instant undoUntil) {
    }

    public record DecisionInput(@NotNull ApplicationStage stage, @NotNull StageDecisionType decision,
                                @Size(max = 1000) String reason) {
    }
}
