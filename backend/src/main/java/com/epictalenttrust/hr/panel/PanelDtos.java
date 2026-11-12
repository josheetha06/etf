package com.epictalenttrust.hr.panel;

import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.report.Recommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The panel login's pages, the review form, and the round report HR sees. */
public final class PanelDtos {

    private PanelDtos() {
    }

    /** What the panel member types on the panel sign-in page. */
    public record AccessInput(@NotBlank @Size(max = 40) String employeeId, @NotBlank @Size(max = 20) String code) {
    }

    /** One score on the form: a skill from the job or a general measure. Score 1-5, or null while still a draft. */
    public record Score(@NotBlank @Size(max = 80) String name, @Min(1) @Max(5) Integer score,
                        @Size(max = 1000) String comment) {
    }

    /** The review as the panel member saves it. Everything can be empty in a draft; Submit checks it's complete. */
    public record ReviewInput(@Size(max = 40) List<@Valid Score> skills, @Size(max = 20) List<@Valid Score> measures,
                              @Size(max = 4000) String strengths, @Size(max = 4000) String concerns,
                              @Size(max = 4000) String questions, @Size(max = 8000) String summary,
                              @Size(max = 4000) String nextRoundNotes, String recommendation) {
    }

    /** A skill the job asks for, and whether it's a must-have. */
    public record Skill(String name, boolean mustHave) {
    }

    /** Everything the review page shows: the interview, the candidate, the form and its saved answers. */
    public record ReviewPage(UUID interviewId, String candidateName, String jobTitle, String companyName, int round,
                             String roundName, RoundKind kind, InterviewMode mode, Instant startAt, Integer durationMin,
                             String meetingLink, String venue, boolean cvAvailable, String jobDescription,
                             List<Skill> skills, List<String> measures, String memberName, String employeeId,
                             ReviewInput review, Instant submittedAt, Double overallScore, boolean editable,
                             Instant openFrom, Instant editableUntil, String closedReason) {
    }

    /** One panel member's review, for HR. */
    public record MemberReview(UUID memberId, String fullName, String employeeId, String email, Instant submittedAt,
                               Double overallScore, String recommendation, List<Score> skills, List<Score> measures,
                               String strengths, String concerns, String questions, String summary,
                               String nextRoundNotes, boolean sample) {
    }

    /** The average for one skill or measure across the panel. */
    public record Average(String name, boolean mustHave, Double average, int scored) {
    }

    /** All the panel's reviews for a round, combined. */
    public record PanelReport(UUID interviewId, int round, String roundName, Instant interviewAt, int members,
                              int submitted, Double overallAverage, Map<String, Integer> recommendations,
                              Recommendation combined, List<Average> skills, List<Average> measures,
                              List<MemberReview> reviews, boolean sample) {
    }
}
