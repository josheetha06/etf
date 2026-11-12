package com.epictalenttrust.hr.cv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateDetailsExtractorTest {

    private final CandidateDetailsExtractor extractor = new CandidateDetailsExtractor();

    @Test
    void readsNameContactAndCity() {
        String cv = """
                Kavya Menon
                DevOps Engineer
                Kochi, India | Kavya.Menon@Example.com | +91 94470 22334
                Summary
                DevOps engineer with 5 years of experience.
                """;
        CandidateDetails details = extractor.extract(cv, "Kavya_Menon_Resume.docx");
        assertThat(details.fullName()).isEqualTo("Kavya Menon");
        assertThat(details.email()).isEqualTo("kavya.menon@example.com");
        assertThat(details.mobile()).isEqualTo("+91 94470 22334");
        assertThat(details.city()).isEqualTo("Kochi");
        assertThat(details.country()).isEqualTo("India");
    }

    @Test
    void fallsBackToTheEmailForTheName() {
        CandidateDetails details = extractor.extract("CURRICULUM VITAE\nemily.clarke@example.co.uk\nLeeds\n", "cv.pdf");
        assertThat(details.fullName()).isEqualTo("Emily Clarke");
        assertThat(details.city()).isEqualTo("Leeds");
        assertThat(details.country()).isEqualTo("United Kingdom");
    }

    @Test
    void normalisesOldCityNamesAndUsesThePhoneForCountry() {
        assertThat(extractor.extract("Ravi Kumar\nBangalore\nravi@example.com", "cv.pdf").city()).isEqualTo("Bengaluru");
        assertThat(extractor.extract("Sam Jones\nsam@example.com\n+44 7700 900123", "cv.pdf").country()).isEqualTo("United Kingdom");
    }

    @Test
    void doesNotMistakeDateRangesForPhoneNumbers() {
        assertThat(extractor.extract("Ann Lee\nann@example.com\n2019 - 2023", "cv.pdf").mobile()).isNull();
    }
}
