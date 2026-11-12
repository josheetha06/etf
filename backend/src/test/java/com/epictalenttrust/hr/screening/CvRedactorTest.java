package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.cv.CandidateDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvRedactorTest {

    @Test
    void removesWhatTheAiMustNotScore() {
        String cv = """
                Priya Das
                priya.das@example.com · +91 98300 88990 · linkedin.com/in/priyadas
                Date of birth: 1 Jan 1995
                Gender: Female
                Address: 12 Park Street, Kolkata
                Priya built dashboards with SQL and Python from 2022 - 2025.
                """;
        String redacted = new CvRedactor().redact(cv, new CandidateDetails("Priya Das", "priya.das@example.com",
                "+91 98300 88990", "Kolkata", "India"));
        assertThat(redacted).doesNotContainIgnoringCase("priya").doesNotContain("@").doesNotContain("98300")
                .doesNotContainIgnoringCase("female").doesNotContainIgnoringCase("birth")
                .doesNotContainIgnoringCase("park street").doesNotContain("linkedin");
        assertThat(redacted).contains("SQL and Python").contains("2022 - 2025");
    }

    @Test
    void removesIndianPersonalDetailsButKeepsSkills() {
        String cv = String.join("\n",
                "Father's Name: Ramesh Kumar",
                "PAN No: ABCDE1234F",
                "Aadhaar: 2345 6789 0123",
                "Passport Number - K1234567",
                "Marital Status: Married",
                "Female",
                "I hereby declare that the above information is true.",
                "Photo editing in Photoshop for marketing teams.",
                "Rolled out a pan-India Salesforce programme; ID 2345 6789 0123 removed.");
        String redacted = new CvRedactor().redact(cv, new CandidateDetails("Ravi Kumar", null, null, null, null));
        assertThat(redacted).doesNotContain("Ramesh").doesNotContain("ABCDE1234F").doesNotContain("2345 6789 0123")
                .doesNotContain("K1234567").doesNotContainIgnoringCase("married").doesNotContain("Female")
                .doesNotContainIgnoringCase("declare");
        assertThat(redacted).contains("Photo editing in Photoshop").contains("pan-India Salesforce programme");
    }
}
