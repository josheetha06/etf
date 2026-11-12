package com.epictalenttrust.hr.screening;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads notice period, CTC, locations and work preferences from lines such as "Notice Period: 60 days",
 * "Current CTC: 12 LPA", "Expected CTC - 18 Lakhs", "Preferred Location: Pune, Bengaluru".
 */
@Component
public class KeyFactsExtractor {

    private static final Pattern NOTICE = Pattern.compile(
            "notice\\s*period[^\\d\\n]{0,20}(\\d{1,3})\\s*(day|days|week|weeks|month|months)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMMEDIATE = Pattern.compile(
            "\\b(immediate(ly)?\\s*(joiner|joining|available)|available\\s*immediately|notice\\s*period[^\\n]{0,15}immediate)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVING = Pattern.compile("\\bserving\\s*(my\\s*)?notice\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LWD = Pattern.compile(
            "\\b(lwd|last\\s*working\\s*day|last\\s*day)\\b[^\\d\\n]{0,10}([0-9]{1,2}[\\s/.-][A-Za-z0-9]{2,9}[\\s/.-][0-9]{2,4})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_CTC = Pattern.compile(
            "\\b(current|present|existing)?\\s*(ctc|salary|package)\\b[^\\d\\n]{0,15}([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(lpa|lakhs?|lacs?|l\\b|lakh\\s*per\\s*annum|cr|crores?|k|per\\s*month|pm)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPECTED_CTC = Pattern.compile(
            "\\b(expected|expecting|desired)\\s*(ctc|salary|package)\\b[^\\d\\n]{0,15}([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(lpa|lakhs?|lacs?|l\\b|lakh\\s*per\\s*annum|cr|crores?|k|per\\s*month|pm)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CURRENT_LOCATION = Pattern.compile(
            "\\bcurrent\\s*(location|city)\\s*[:\\-–]\\s*([A-Za-z .,]{2,60})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFERRED_LOCATION = Pattern.compile(
            "\\bpreferred\\s*(job\\s*)?(locations?|cities)\\s*[:\\-–]\\s*([A-Za-z ,/&.]{2,120})", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELOCATE = Pattern.compile(
            "\\b(not\\s+)?(willing|open|ready)\\s*to\\s*relocate\\b\\s*[:\\-–]?\\s*(yes|no)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORK_MODE = Pattern.compile(
            "\\b(work\\s*mode|work\\s*preference|preferred\\s*(work\\s*)?mode)\\b[^\\n]{0,20}\\b(?<mode>remote|hybrid|office|onsite|on-site|work from home|wfh|any)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> DATES = List.of(
            pattern("d MMM yyyy"), pattern("d MMMM yyyy"), pattern("d/M/yyyy"), pattern("d-M-yyyy"), pattern("d.M.yyyy"),
            pattern("d-MMM-yyyy"), pattern("d/M/yy"));

    public KeyFacts extract(String text) {
        Integer notice = null;
        Boolean serving = null;
        LocalDate lwd = null;
        Double currentCtc = null;
        Double expectedCtc = null;
        String currentLocation = null;
        List<String> preferred = new ArrayList<>();
        Boolean relocate = null;
        String workMode = null;
        for (String line : TermMatcher.normalise(text).split("\\R")) {
            if (notice == null) {
                Matcher m = NOTICE.matcher(line);
                if (m.find()) {
                    int value = Integer.parseInt(m.group(1));
                    String unit = m.group(2).toLowerCase(Locale.ROOT);
                    notice = unit.startsWith("month") ? value * 30 : unit.startsWith("week") ? value * 7 : value;
                } else if (IMMEDIATE.matcher(line).find()) {
                    notice = 0;
                }
            }
            if (SERVING.matcher(line).find()) {
                serving = true;
            }
            if (lwd == null) {
                Matcher m = LWD.matcher(line);
                if (m.find()) {
                    lwd = date(m.group(2));
                    serving = serving == null ? Boolean.TRUE : serving;
                }
            }
            Matcher expected = EXPECTED_CTC.matcher(line);
            if (expectedCtc == null && expected.find()) {
                expectedCtc = lpa(expected.group(3), expected.group(4));
            } else if (currentCtc == null) {
                Matcher current = CURRENT_CTC.matcher(line);
                if (current.find() && !line.toLowerCase(Locale.ROOT).contains("expect")) {
                    currentCtc = lpa(current.group(3), current.group(4));
                }
            }
            if (currentLocation == null) {
                Matcher m = CURRENT_LOCATION.matcher(line);
                if (m.find()) {
                    currentLocation = tidy(m.group(2));
                }
            }
            if (preferred.isEmpty()) {
                Matcher m = PREFERRED_LOCATION.matcher(line);
                if (m.find()) {
                    Arrays.stream(m.group(3).split("[,/&]|\\band\\b")).map(KeyFactsExtractor::tidy)
                            .filter(s -> s.length() > 1).forEach(preferred::add);
                }
            }
            if (relocate == null) {
                Matcher m = RELOCATE.matcher(line);
                if (m.find()) {
                    relocate = m.group(1) == null && !"no".equalsIgnoreCase(m.group(3));
                }
            }
            if (workMode == null) {
                Matcher m = WORK_MODE.matcher(line);
                if (m.find()) {
                    String mode = m.group("mode").toLowerCase(Locale.ROOT);
                    workMode = switch (mode) {
                        case "remote", "work from home", "wfh" -> "remote";
                        case "hybrid" -> "hybrid";
                        case "any" -> "any";
                        default -> "office";
                    };
                }
            }
        }
        return new KeyFacts(notice, serving, lwd, currentCtc, expectedCtc, currentLocation, List.copyOf(preferred),
                relocate, workMode);
    }

    /** Amounts to lakhs per annum: "12 LPA", "12,00,000", "1.2 Cr", "80k per month". */
    static Double lpa(String number, String unit) {
        double value;
        try {
            value = Double.parseDouble(number.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
        String u = unit == null ? "" : unit.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        double lakhs;
        if (u.startsWith("cr")) {
            lakhs = value * 100;
        } else if (u.equals("k") || u.contains("month") || u.equals("pm")) {
            lakhs = (u.equals("k") ? value * 1000 : value) * (u.contains("month") || u.equals("pm") ? 12 : 1) / 100_000;
        } else if (u.startsWith("l") || u.startsWith("lakh") || u.startsWith("lac")) {
            lakhs = value;
        } else if (value >= 10_000) {
            lakhs = value / 100_000;
        } else if (value <= 300) {
            lakhs = value;
        } else {
            return null;
        }
        if (lakhs <= 0 || lakhs > 1000) {
            return null;
        }
        return Math.round(lakhs * 100) / 100.0;
    }

    private static LocalDate date(String text) {
        String cleaned = text.strip().replaceAll("\\s+", " ");
        for (DateTimeFormatter format : DATES) {
            try {
                return LocalDate.parse(cleaned, format);
            } catch (RuntimeException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static DateTimeFormatter pattern(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
    }

    private static String tidy(String value) {
        String cleaned = value.replaceAll("\\s+", " ").replaceAll("[.,\\s]+$", "").strip();
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        return Arrays.stream(cleaned.split(" "))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1).toLowerCase(Locale.ROOT))
                .reduce((a, b) -> a + " " + b).orElse(cleaned);
    }
}
