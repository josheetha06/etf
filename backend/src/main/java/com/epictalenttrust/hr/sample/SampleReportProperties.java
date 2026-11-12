package com.epictalenttrust.hr.sample;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sample reports stand in for the AI interviewer (Round 1), the partner assessor (Round 2) and the hiring panel
 * (Round 3) until those are connected. Every sample report is labelled "SAMPLE" in the portal. Turn this off in a
 * live installation.
 *
 * @param enabled            show "Complete with sample report" and fill in reports automatically
 * @param autoCompleteMinutes after an interview's end time, how long to wait before filling in a sample report
 *                           automatically (0 turns automatic completion off)
 */
@ConfigurationProperties(prefix = "ett.sample-reports")
public record SampleReportProperties(boolean enabled, int autoCompleteMinutes) {
}
