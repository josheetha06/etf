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

class EvidenceScoringEngineTest {

    private static final YearMonth NOW = YearMonth.of(2026, 11);
    private final EvidenceScoringEngine engine = new EvidenceScoringEngine(
            new ExperienceEstimator(Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC)));
    private final SkillDictionary.Vocabulary vocabulary = new SkillDictionary(null).vocabulary(null);

    private static ScreeningCriterion c(CriterionKind kind, String value) {
        return new ScreeningCriterion(UUID.randomUUID(), kind, value);
    }

    private static final List<ScreeningCriterion> SALESFORCE_QA = List.of(
            c(CriterionKind.MUST_HAVE, "Automation"), c(CriterionKind.MUST_HAVE, "QA"),
            c(CriterionKind.MUST_HAVE, "salesforce"), c(CriterionKind.NICE_TO_HAVE, "aws"));

    private ScreeningOutcome screen(String cv, List<ScreeningCriterion> criteria, Integer minYears) {
        return engine.screen(new ScreeningInput(cv, criteria, minYears, 70, 50, vocabulary, NOW));
    }

    static String padded(String... lines) {
        // Real CVs are longer; pad with neutral text so the reading-quality check sees a normal CV.
        return String.join("\n", lines) + "\nINTERESTS\nCricket, reading and travelling with family on weekends.\n"
                + "Volunteering with a local school to teach basic computer skills to children every month.\n"
                + "Member of the city running club and the photography society, organising monthly walks.\n"
                + "Enjoys mentoring junior colleagues, writing short blog posts and cooking for friends.";
    }

    @Test
    void realSalesforceQaEngineerIsShortlisted() {
        ScreeningOutcome o = screen(padded(
                "EXPERIENCE",
                "Senior QA Engineer, Infosys  Jan 2022 - Present",
                "Built Selenium and Provar test automation for Salesforce Sales Cloud; 900 regression tests in CI.",
                "QA Engineer, TCS  Jun 2020 - Dec 2021",
                "Manual and automated testing of Salesforce Service Cloud releases.",
                "SKILLS: Selenium, Provar, Apex, AWS"), SALESFORCE_QA, 4);
        assertThat(o.matchScore()).isEqualTo(89);
        assertThat(o.suggestion()).isEqualTo(AiSuggestion.SHORTLIST);
        assertThat(o.evidence().get(3).strength()).isEqualTo(EvidenceStrength.SKILLS_ONLY);
        assertThat(o.relevantYears()).isGreaterThanOrEqualTo(6.0);
    }

    @Test
    void adminWithQaOnlyInSkillsListGoesToReserveWithAReason() {
        ScreeningOutcome o = screen(padded(
                "EXPERIENCE",
                "Salesforce Administrator, Wipro  Mar 2019 - Present",
                "Managed users, profiles, flows and reports for 2,000 Salesforce users.",
                "SKILLS: Salesforce, Flows, Reports, QA, Automation"), SALESFORCE_QA, 4);
        assertThat(o.matchScore()).isEqualTo(59);
        assertThat(o.suggestion()).isEqualTo(AiSuggestion.REVIEW);
        assertThat(o.reason()).contains("Automation and QA are only listed as skills");
    }

    @Test
    void differentWordingStillCounts() {
        ScreeningOutcome o = screen(padded(
                "EXPERIENCE",
                "Quality Assurance Engineer, HCL  Jan 2021 - Present",
                "Automated regression testing of SFDC (Sales Cloud) with Selenium.",
                "SKILLS: Selenium, Java, TestNG"), SALESFORCE_QA, 4);
        assertThat(o.matchScore()).isEqualTo(78);
        assertThat(o.suggestion()).isEqualTo(AiSuggestion.SHORTLIST);
    }

    @Test
    void unusedSectionsGiveNoFreePoints() {
        ScreeningOutcome o = screen(padded("SUMMARY", "Accountant with Tally experience."),
                List.of(c(CriterionKind.MUST_HAVE, "Kubernetes")), null);
        assertThat(o.matchScore()).isZero();
        assertThat(o.certificationPts()).isZero();
        assertThat(o.experiencePts()).isZero();
    }

    @Test
    void negationAndWeakMentions() {
        ScreeningOutcome o = screen(padded(
                "SUMMARY",
                "Developer with no experience in Kubernetes yet.",
                "TRAINING",
                "Basic knowledge of Docker from an online course."),
                List.of(c(CriterionKind.MUST_HAVE, "Kubernetes"), c(CriterionKind.MUST_HAVE, "Docker")), null);
        assertThat(o.evidence().get(0).strength()).isEqualTo(EvidenceStrength.NEGATED);
        assertThat(o.evidence().get(1).strength()).isEqualTo(EvidenceStrength.MENTION_ONLY);
    }

    @Test
    void certificationsCountOnlyAsCertifications() {
        ScreeningOutcome o = screen(padded(
                "Senior Salesforce QA Analyst | 4 Years Experience",
                "EXPERIENCE",
                "QA Analyst, Accenture  Jan 2022 - Present",
                "Tested Salesforce releases."),
                List.of(c(CriterionKind.MUST_HAVE, "QA"), c(CriterionKind.CERTIFICATION, "salesforce")), null);
        assertThat(o.evidence().get(1).matched()).as("job title is not a certification").isFalse();

        ScreeningOutcome certified = screen(padded(
                "CERTIFICATIONS",
                "Salesforce Certified Administrator (2024)",
                "EXPERIENCE",
                "QA Analyst, Accenture  Jan 2022 - Present"),
                List.of(c(CriterionKind.MUST_HAVE, "QA"), c(CriterionKind.CERTIFICATION, "Salesforce Administrator")), null);
        assertThat(certified.evidence().get(1).matched()).isTrue();
    }

    @Test
    void minimumYearsPerSkill() {
        ScreeningCriterion salesforce = new ScreeningCriterion(UUID.randomUUID(), CriterionKind.MUST_HAVE, "Salesforce", 3);
        ScreeningOutcome o = screen(padded(
                "EXPERIENCE",
                "Developer, Wipro  Jan 2025 - Present",
                "Salesforce Apex development for service teams."), List.of(salesforce), null);
        assertThat(o.evidence().getFirst().skillYears()).isBetween(1.8, 2.0);
        assertThat(o.suggestion()).isNotEqualTo(AiSuggestion.SHORTLIST);
        assertThat(o.reason()).contains("of 3 years asked");
    }

    @Test
    void messyTextIsSentForAManualCheck() {
        ScreeningOutcome o = screen("@@@ ### 123 456 /// ;;; Java", List.of(c(CriterionKind.MUST_HAVE, "Java")), null);
        assertThat(o.readable()).isFalse();
        assertThat(o.suggestion()).isEqualTo(AiSuggestion.REVIEW);
    }
}
