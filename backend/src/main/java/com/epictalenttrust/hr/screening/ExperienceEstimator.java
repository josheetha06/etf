package com.epictalenttrust.hr.screening;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estimates years of work experience from a CV: the larger of what the candidate states
 * ("6 years of experience") and the total of employment date ranges outside the education section.
 */
@Component
public class ExperienceEstimator {

    private static final int MAX_YEARS = 45;
    private static final String MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final Pattern RANGE = Pattern.compile(
            "(?:" + MONTH + "\\s*)?((?:19|20)\\d{2})\\s*(?:-|to|until)\\s*(?:(?:" + MONTH + "\\s*)?((?:19|20)\\d{2})|(present|current|now|date|today))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STATED = Pattern.compile(
            "(\\d{1,2})(?:\\.\\d)?\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDUCATION_LINE = Pattern.compile(
            "\\b(b\\.?tech|m\\.?tech|b\\.e\\b|m\\.e\\b|b\\.?sc|m\\.?sc|bca|mca|mba|ph\\.?d|bachelor|master|degree|university|college|school|diploma|graduat)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^[A-Za-z &/]{3,40}:?$");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private final Clock clock;

    public ExperienceEstimator(Clock clock) {
        this.clock = clock;
    }

    public int estimateYears(String text) {
        return Math.min(MAX_YEARS, Math.max(statedYears(text), rangeYears(text)));
    }

    int statedYears(String text) {
        int best = 0;
        for (String line : text.split("\\R")) {
            if (!line.toLowerCase(Locale.ROOT).contains("experience")) {
                continue;
            }
            Matcher matcher = STATED.matcher(line);
            while (matcher.find()) {
                int years = Integer.parseInt(matcher.group(1));
                if (years <= MAX_YEARS) {
                    best = Math.max(best, years);
                }
            }
        }
        return best;
    }

    int rangeYears(String text) {
        YearMonth now = YearMonth.now(clock);
        List<int[]> spans = new ArrayList<>();
        boolean inEducation = false;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (HEADING.matcher(line).matches() && line.length() < 40) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("education") || lower.contains("academic") || lower.contains("qualification")) {
                    inEducation = true;
                    continue;
                }
                if (lower.contains("experience") || lower.contains("employment") || lower.contains("work history")
                        || lower.contains("career") || lower.contains("skills") || lower.contains("projects")
                        || lower.contains("certification")) {
                    inEducation = false;
                }
            }
            if (inEducation || EDUCATION_LINE.matcher(line).find()) {
                continue;
            }
            Matcher matcher = RANGE.matcher(line);
            while (matcher.find()) {
                int startMonth = monthOf(matcher.group(1), 1);
                int startYear = Integer.parseInt(matcher.group(2));
                int endYear;
                int endMonth;
                if (matcher.group(5) != null) {
                    endYear = now.getYear();
                    endMonth = now.getMonthValue();
                } else {
                    endYear = Integer.parseInt(matcher.group(4));
                    endMonth = monthOf(matcher.group(3), 12);
                }
                int start = startYear * 12 + startMonth - 1;
                int end = Math.min(endYear * 12 + endMonth - 1, now.getYear() * 12 + now.getMonthValue() - 1);
                if (end > start && startYear >= 1970) {
                    spans.add(new int[]{start, end});
                }
            }
        }
        return mergedMonths(spans) / 12;
    }

    private static int monthOf(String name, int fallback) {
        if (name == null) {
            return fallback;
        }
        return MONTHS.getOrDefault(name.toLowerCase(Locale.ROOT).substring(0, 3), fallback);
    }

    private static int mergedMonths(List<int[]> spans) {
        spans.sort(Comparator.comparingInt(s -> s[0]));
        int total = 0;
        int currentStart = -1;
        int currentEnd = -1;
        for (int[] span : spans) {
            if (span[0] > currentEnd) {
                if (currentEnd > currentStart) {
                    total += currentEnd - currentStart;
                }
                currentStart = span[0];
                currentEnd = span[1];
            } else {
                currentEnd = Math.max(currentEnd, span[1]);
            }
        }
        if (currentEnd > currentStart) {
            total += currentEnd - currentStart;
        }
        return total;
    }
}
