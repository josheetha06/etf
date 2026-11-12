package com.epictalenttrust.hr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ett")
public record EttProperties(
        String portalUrl,
        Security security,
        Storage storage,
        Mail mail,
        Retention retention,
        Scheduling scheduling) {

    public record Security(String encryptionKey, String totpIssuer, int maxFailedSignIns, int lockoutMinutes) {
    }

    public record Storage(String localDir) {
    }

    /** Sender and email colours. The colours should match frontend/src/brand.ts. */
    public record Mail(String from, String brandColor, String headingColor) {
    }

    public record Retention(int cvDays) {
    }

    public record Scheduling(boolean jobsEnabled) {
    }
}
