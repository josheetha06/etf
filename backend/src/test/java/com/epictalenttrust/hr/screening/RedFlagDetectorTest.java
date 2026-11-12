package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.job.CriterionKind;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedFlagDetectorTest {

    private static final YearMonth NOW = YearMonth.of(2026, 11);
    private final EvidenceScoringEngine engine = new EvidenceScoringEngine(
            new ExperienceEstimator(Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC)));
    private final RedFlagDetector detector = new RedFlagDetector();
    private final KeyFactsExtractor facts = new KeyFactsExtractor();

    private List<FlagFinding> flags(String cv, RedFlagDetector.JobLimits limits) {
        ScreeningOutcome outcome = engine.screen(new ScreeningInput(EvidenceScoringEngineTest.padded(cv),
                List.of(new ScreeningCriterion(UUID.randomUUID(), CriterionKind.MUST_HAVE, "Java")), null, 70, 50,
                new SkillDictionary(null).vocabulary(null), NOW));
        return detector.detect(outcome, true, true, facts.extract(cv), limits, RedFlagDetector.Thresholds.DEFAULT, NOW);
    }

    private static List<String> kinds(List<FlagFinding> flags) {
        return flags.stream().map(FlagFinding::kind).toList();
    }

    @Test
    void cleanCvHasNoFlags() {
        assertThat(flags("""
                SUMMARY
                Java developer.
                EXPERIENCE
                Developer, Infosys  Jan 2023 - Present
                Java services.
                Developer, TCS  Jan 2020 - Dec 2022
                Java batch jobs.""", RedFlagDetector.JobLimits.NONE)).isEmpty();
    }

    @Test
    void overlappingJobsDatesAndClaims() {
        List<FlagFinding> found = flags("""
                SUMMARY
                Java developer with 12 years of experience.
                EXPERIENCE
                Developer, Infosys  Jan 2022 - Present
                Java services.
                Developer, Wipro  Mar 2022 - Dec 2024
                Java batch jobs.
                Developer, HCL  Jan 2028 - Feb 2028
                Java.""", RedFlagDetector.JobLimits.NONE);
        assertThat(kinds(found)).contains("overlapping_jobs", "future_dates", "experience_claim");
        assertThat(found).filteredOn(f -> f.kind().equals("overlapping_jobs"))
                .allMatch(f -> f.severity() == FlagFinding.Severity.RED);
    }

    @Test
    void gapsShortJobsAndJobLimits() {
        List<FlagFinding> found = flags("""
                Notice Period: 90 days
                Expected CTC: 24 LPA
                Current Location: Kolkata
                EXPERIENCE
                Developer, A  Jan 2024 - Jun 2024
                Java.
                Developer, B  Aug 2024 - Feb 2025
                Java.
                Developer, C  Mar 2025 - Sep 2025
                Java.
                Developer, D  Jan 2018 - Dec 2018
                Java.""", new RedFlagDetector.JobLimits(30, 18.0, List.of("Bengaluru", "Pune")));
        assertThat(kinds(found)).contains("employment_gap", "not_working", "short_jobs", "notice_period", "ctc_budget",
                "location");
        assertThat(found).filteredOn(f -> f.kind().equals("notice_period"))
                .allMatch(f -> f.severity() == FlagFinding.Severity.CHECK);
    }

    @Test
    void readsIndianKeyFacts() {
        KeyFacts k = facts.extract("""
                Notice Period: 2 months (serving notice, LWD: 15 Dec 2026)
                Current CTC: 12,00,000
                Expected CTC - 18 Lakhs
                Current Location: bangalore
                Preferred Locations: Pune, Hyderabad and Chennai
                Willing to relocate: Yes""");
        assertThat(k.noticePeriodDays()).isEqualTo(60);
        assertThat(k.servingNotice()).isTrue();
        assertThat(k.lastWorkingDay()).hasToString("2026-12-15");
        assertThat(k.currentCtcLpa()).isEqualTo(12.0);
        assertThat(k.expectedCtcLpa()).isEqualTo(18.0);
        assertThat(k.currentLocation()).isEqualTo("Bangalore");
        assertThat(k.preferredLocations()).containsExactly("Pune", "Hyderabad", "Chennai");
        assertThat(k.willingToRelocate()).isTrue();
        assertThat(facts.extract("Immediate joiner").noticePeriodDays()).isZero();
    }
}
