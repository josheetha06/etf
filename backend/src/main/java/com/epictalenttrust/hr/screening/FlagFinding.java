package com.epictalenttrust.hr.screening;

/**
 * Something HR should look at in a CV. {@code RED}: possibly false information. {@code CHECK}: worth asking about.
 * Flags never change the score.
 */
public record FlagFinding(Severity severity, String kind, String message, String cvQuote) {

    public enum Severity { RED, CHECK }

    public static FlagFinding red(String kind, String message, String quote) {
        return new FlagFinding(Severity.RED, kind, message, quote);
    }

    public static FlagFinding check(String kind, String message, String quote) {
        return new FlagFinding(Severity.CHECK, kind, message, quote);
    }
}
