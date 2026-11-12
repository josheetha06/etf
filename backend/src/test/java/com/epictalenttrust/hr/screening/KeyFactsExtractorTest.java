package com.epictalenttrust.hr.screening;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeyFactsExtractorTest {

    private final KeyFactsExtractor extractor = new KeyFactsExtractor();

    @Test
    void readsTheDetailsIndianCvsUsuallyList() {
        KeyFacts facts = extractor.extract("""
                Other details
                Notice period: 90 days
                Current CTC: 18 LPA
                Expected CTC: 26 LPA
                Current location: Bengaluru
                Preferred locations: Bengaluru, Pune
                Work mode: Hybrid
                """);
        assertThat(facts.noticePeriodDays()).isEqualTo(90);
        assertThat(facts.currentCtcLpa()).isEqualTo(18.0);
        assertThat(facts.expectedCtcLpa()).isEqualTo(26.0);
        assertThat(facts.currentLocation()).isEqualTo("Bengaluru");
        assertThat(facts.preferredLocations()).containsExactly("Bengaluru", "Pune");
        assertThat(facts.workMode()).isEqualTo("hybrid");
    }

    @Test
    void readsServingNoticeAndRelocation() {
        KeyFacts facts = extractor.extract("""
                Serving notice period. Last working day: 28 Nov 2026
                Willing to relocate: No
                Preferred work mode - remote
                """);
        assertThat(facts.servingNotice()).isTrue();
        assertThat(facts.lastWorkingDay()).isEqualTo(LocalDate.of(2026, 11, 28));
        assertThat(facts.willingToRelocate()).isFalse();
        assertThat(facts.workMode()).isEqualTo("remote");
    }

    @Test
    void keepsTheAreaAndCityOfTheCurrentLocation() {
        assertThat(extractor.extract("Current location: Anna Nagar, Madurai\n").currentLocation()).isEqualTo("Anna Nagar, Madurai");
    }

    @Test
    void emptyWhenNothingIsListed() {
        assertThat(extractor.extract("Built CI/CD pipelines on AWS.").isEmpty()).isTrue();
        assertThat(KeyFacts.EMPTY.preferredLocations()).isEqualTo(List.of());
    }
}
