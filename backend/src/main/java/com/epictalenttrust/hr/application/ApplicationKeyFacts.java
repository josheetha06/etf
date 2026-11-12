package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.AuditedEntity;
import com.epictalenttrust.hr.screening.KeyFacts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Notice period, CTC, locations and experience for one candidate in one job. HR can correct anything read from the CV. */
@Entity
@Table(name = "application_key_facts")
public class ApplicationKeyFacts extends AuditedEntity {

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "notice_period_days")
    private Integer noticePeriodDays;

    @Column(name = "serving_notice")
    private Boolean servingNotice;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "current_ctc_lpa")
    private Double currentCtcLpa;

    @Column(name = "expected_ctc_lpa")
    private Double expectedCtcLpa;

    @Column(name = "current_location")
    private String currentLocation;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_locations", columnDefinition = "text[]")
    private List<String> preferredLocations;

    @Column(name = "total_experience_years")
    private Double totalExperienceYears;

    @Column(name = "relevant_experience_years")
    private Double relevantExperienceYears;

    @Column(name = "willing_to_relocate")
    private Boolean willingToRelocate;

    @Column(name = "work_mode")
    private String workMode;

    /** Where the notice period came from: cv, ai_interview or hr. */
    @Column(name = "notice_source")
    private String noticeSource;

    /** Where the expected CTC came from: cv, ai_interview or hr. */
    @Column(name = "ctc_source")
    private String ctcSource;

    protected ApplicationKeyFacts() {
    }

    public ApplicationKeyFacts(UUID applicationId) {
        this.applicationId = applicationId;
    }

    /** Fills only what's missing, so anything HR typed is kept. */
    public void fillFromCv(KeyFacts facts, double totalYears, double relevantYears) {
        if (noticePeriodDays == null && facts.noticePeriodDays() != null) {
            noticePeriodDays = facts.noticePeriodDays();
            noticeSource = "cv";
        }
        if (servingNotice == null) servingNotice = facts.servingNotice();
        if (lastWorkingDay == null) lastWorkingDay = facts.lastWorkingDay();
        if (currentCtcLpa == null) currentCtcLpa = facts.currentCtcLpa();
        if (expectedCtcLpa == null && facts.expectedCtcLpa() != null) {
            expectedCtcLpa = facts.expectedCtcLpa();
            ctcSource = "cv";
        }
        if (currentLocation == null) currentLocation = facts.currentLocation();
        if ((preferredLocations == null || preferredLocations.isEmpty()) && facts.preferredLocations() != null
                && !facts.preferredLocations().isEmpty()) {
            preferredLocations = facts.preferredLocations();
        }
        if (willingToRelocate == null) willingToRelocate = facts.willingToRelocate();
        if (workMode == null) workMode = facts.workMode();
        if (totalExperienceYears == null && totalYears > 0) totalExperienceYears = totalYears;
        if (relevantExperienceYears == null && relevantYears > 0) relevantExperienceYears = relevantYears;
    }

    /** The AI interviewer asked for what the CV left out; anything already known is kept. */
    public void fillFromAiInterview(Integer noticeDays, Double expectedCtc) {
        if (noticePeriodDays == null && noticeDays != null) {
            noticePeriodDays = noticeDays;
            noticeSource = "ai_interview";
        }
        if (expectedCtcLpa == null && expectedCtc != null) {
            expectedCtcLpa = expectedCtc;
            ctcSource = "ai_interview";
        }
    }

    public void update(KeyFacts facts, Double totalYears, Double relevantYears) {
        if (!java.util.Objects.equals(noticePeriodDays, facts.noticePeriodDays())) {
            noticeSource = facts.noticePeriodDays() == null ? null : "hr";
        }
        if (!java.util.Objects.equals(expectedCtcLpa, facts.expectedCtcLpa())) {
            ctcSource = facts.expectedCtcLpa() == null ? null : "hr";
        }
        this.noticePeriodDays = facts.noticePeriodDays();
        this.servingNotice = facts.servingNotice();
        this.lastWorkingDay = facts.lastWorkingDay();
        this.currentCtcLpa = facts.currentCtcLpa();
        this.expectedCtcLpa = facts.expectedCtcLpa();
        this.currentLocation = facts.currentLocation();
        this.preferredLocations = facts.preferredLocations();
        this.willingToRelocate = facts.willingToRelocate();
        this.workMode = facts.workMode();
        this.totalExperienceYears = totalYears;
        this.relevantExperienceYears = relevantYears;
    }

    public KeyFacts toKeyFacts() {
        return new KeyFacts(noticePeriodDays, servingNotice, lastWorkingDay, currentCtcLpa, expectedCtcLpa,
                currentLocation, preferredLocations == null ? List.of() : preferredLocations, willingToRelocate, workMode);
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getNoticeSource() {
        return noticeSource;
    }

    public String getCtcSource() {
        return ctcSource;
    }

    public Double getTotalExperienceYears() {
        return totalExperienceYears;
    }

    public Double getRelevantExperienceYears() {
        return relevantExperienceYears;
    }
}
