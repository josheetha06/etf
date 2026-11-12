package com.epictalenttrust.hr.screening;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermMatcherTest {

    private static boolean matches(String term, String line) {
        return TermMatcher.find(TermMatcher.tokens(line), TermMatcher.phrase(term)).isPresent();
    }

    @Test
    void wordFormsAndWordOrder() {
        assertThat(matches("Automation", "Automated regression suites")).isTrue();
        assertThat(matches("automation testing", "Built a test automation framework")).isTrue();
        assertThat(matches("automation testing", "Automated tests for checkout")).isTrue();
        assertThat(matches("Engineering", "Software engineer at Infosys")).isTrue();
        assertThat(matches("Management", "Managed a team of 8")).isTrue();
    }

    @Test
    void wholeWordsAndSymbols() {
        assertThat(matches("Java", "JavaScript and TypeScript")).isFalse();
        assertThat(matches("React", "Reactive programming")).isFalse();
        assertThat(matches("C++", "Wrote C++ services")).isTrue();
        assertThat(matches(".NET", "Built .NET Core APIs")).isTrue();
        assertThat(matches("Node.js", "Node.js and Express")).isTrue();
        assertThat(matches("Go", "Responsible for go-live support")).isFalse();
        assertThat(matches("Go", "Services written in Go and Python")).isTrue();
    }
}
