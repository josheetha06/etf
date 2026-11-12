package com.epictalenttrust.hr.job;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {
    }

    /**
     * Create or update a job. {@code rounds} is the job's interview plan in order (Round 1 first); left out, a new job
     * starts from the company's default plan. {@code rescore} confirms that changed criteria may re-score the CVs
     * already in the job.
     */
    public record JobRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 150) String location,
            ContractType contractType,
            @Min(1) @Max(500) Integer openings,
            @Min(0) @Max(40) Integer minExperienceYrs,
            @Size(max = 40) List<@Valid CriterionInput> criteria,
            @Size(max = 8) List<@Valid RoundInput> rounds,
            @Min(0) @Max(365) Integer maxNoticeDays,
            @DecimalMin("0") @DecimalMax("1000") Double maxCtcLpa,
            @Size(max = 20) List<@NotBlank @Size(max = 60) String> acceptedLocations,
            Boolean rescore,
            Boolean aiShortlisting,
            @Size(max = 20000) String description,
            @Size(max = 200) String descriptionFileName,
            UUID hiringManagerId,
            UUID ownerId) {

        public JobRequest(String title, String location, ContractType contractType, Integer openings,
                          Integer minExperienceYrs, List<CriterionInput> criteria, List<RoundInput> rounds,
                          Integer maxNoticeDays, Double maxCtcLpa, List<String> acceptedLocations, Boolean rescore) {
            this(title, location, contractType, openings, minExperienceYrs, criteria, rounds, maxNoticeDays, maxCtcLpa,
                    acceptedLocations, rescore, null, null, null, null, null);
        }
    }

    public record CriterionInput(@NotNull CriterionKind kind, @NotBlank @Size(max = 80) String value,
                                 @Min(0) @Max(40) Integer minYears) {

        public CriterionInput(CriterionKind kind, String value) {
            this(kind, value, null);
        }
    }

    /** One round of the plan. Partner rounds are always in person and AI interviews always online. */
    public record RoundInput(@NotBlank @Size(max = 60) String name, @NotNull RoundKind kind, InterviewMode mode,
                             @Min(15) @Max(480) int durationMin, @Min(1) @Max(90) Integer targetDays) {
    }

    public record CriterionView(UUID id, CriterionKind kind, String value, Integer minYears, List<String> alsoMatches) {
    }

    public record RoundView(int round, String name, RoundKind kind, InterviewMode mode, Integer durationMin,
                            Integer targetDays) {
    }

    /** How many candidates are in each round right now, and how many of them are past the round's target time. */
    public record RoundCount(int round, String name, RoundKind kind, int count, int overdue) {
    }

    /** A person shown on a job: owner, hiring manager, who created or last changed it. */
    public record PersonRef(UUID id, String name) {
    }

    /** Who created and last changed a record, for the lists and page headers. */
    public record Changes(String createdByName, Instant createdAt, String lastUpdatedByName, Instant lastUpdatedAt) {
    }

    public record Counts(int cvs, int screened, int aiShortlist, int shortlisted, int interviews) {
    }

    /** Candidates at each step, for the pipeline view: screening, shortlisted, each round, offer and outcomes. */
    public record Pipeline(int screening, int shortlisted, List<RoundCount> rounds, int offer, int hired,
                           int notProgressing, int withdrawn) {
    }

    public record JobSummary(UUID id, String jobRef, String title, String location, ContractType contractType,
                             Integer openings, JobStatus status, Instant publishedAt, Instant createdAt, Counts counts,
                             boolean aiShortlisting, String statusReason, PersonRef owner, PersonRef hiringManager,
                             Changes changes, List<RoundCount> rounds, boolean starred) {
    }

    public record JobDetail(UUID id, String jobRef, String title, String location, ContractType contractType,
                            Integer openings, Integer minExperienceYrs, JobStatus status, Instant publishedAt,
                            Instant shortlistConfirmedAt, Instant createdAt, String createdByName,
                            List<CriterionView> criteria, List<RoundView> rounds, Counts counts,
                            boolean criteriaLocked, int criteriaVersion, Integer maxNoticeDays, Double maxCtcLpa,
                            List<String> acceptedLocations, Pipeline pipeline, boolean aiShortlisting,
                            String description, String descriptionFileName, PersonRef owner, PersonRef hiringManager,
                            String statusReason, Instant statusChangedAt, String statusChangedByName,
                            int roundsLockedUpTo, Changes changes, String coveringFor, boolean starred) {
    }

    /** HR puts a job on hold, closes it, or opens it again. A reason is needed to hold or close. */
    public record StatusChange(@NotNull JobStatus status, @Size(max = 500) String reason) {
    }

    /** Text read from an uploaded job description, and the skills found in it. */
    public record DescriptionRead(String text, String fileName, List<String> mustHave, List<String> niceToHave) {
    }

    public record DescriptionText(@NotBlank @Size(max = 20000) String text) {
    }

    public record CriteriaCheckRequest(@Size(max = 40) List<@Valid CriterionInput> criteria) {
    }

    public record CriteriaCheck(List<CriteriaAdvisor.Item> items, boolean blocking) {
    }
}
