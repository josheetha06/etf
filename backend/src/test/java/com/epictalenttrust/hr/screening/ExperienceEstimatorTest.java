package com.epictalenttrust.hr.screening;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceEstimatorTest {

    private final ExperienceEstimator estimator =
            new ExperienceEstimator(Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void addsUpEmploymentRangesWithoutDoubleCountingOverlaps() {
        String cv = """
                EXPERIENCE
                Senior Engineer, Acme  Mar 2022 - Present
                Engineer, Beta  Jun 2019 - Feb 2022
                Contractor, Gamma  2020 - 2021
                """;
        assertThat(estimator.estimateYears(cv)).isEqualTo(7);
    }

    @Test
    void ignoresEducationDates() {
        String cv = """
                Engineer, Acme  Jul 2020 - Present
                EDUCATION
                BSc Computer Science, University of Leeds  2016 - 2019
                """;
        assertThat(estimator.estimateYears(cv)).isEqualTo(6);
    }

    @Test
    void ignoresDegreeLinesOutsideAnEducationSection() {
        assertThat(estimator.estimateYears("B.Tech, Anna University 2014 - 2018")).isZero();
    }

    @Test
    void usesStatedExperienceWhenThereAreNoDates() {
        assertThat(estimator.estimateYears("Cloud engineer with 6 years of experience in AWS.")).isEqualTo(6);
        assertThat(estimator.estimateYears("Worked on 3 projects")).isZero();
    }
}
