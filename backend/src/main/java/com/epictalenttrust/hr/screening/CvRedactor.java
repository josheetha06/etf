package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.cv.CandidateDetails;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Removes the things the AI must never score on before a CV is scored: the candidate's name and contact details,
 * and personal details common on Indian CVs (date of birth, gender, marital status, religion, caste, father's or
 * spouse's name, address, photo) plus identity numbers (PAN, Aadhaar, passport). Skills and work history stay.
 */
@Component
public class CvRedactor {

    /** Labelled personal details, e.g. "Father's Name: …", "PAN No - …", "Marital Status: Married". */
    private static final Pattern PERSONAL_LABEL = Pattern.compile(
            "^\\s*(permanent address|present address|current address|residential address|address|date of birth|"
                    + "d\\.?\\s?o\\.?\\s?b|age|gender|sex|marital status|nationality|religion|caste|category|community|"
                    + "father'?s?\\s+name|mother'?s?\\s+name|husband'?s?\\s+name|spouse'?s?\\s+name|wife'?s?\\s+name|"
                    + "guardian'?s?\\s+name|father|mother|spouse|husband|passport(\\s+(no|number))?|"
                    + "pan(\\s+(no|number|card))?|aadhaa?r(\\s+(no|number|card))?|uid|voter\\s+id|"
                    + "driving\\s+licen[cs]e(\\s+no)?|blood\\s+group|height|weight|photo(graph)?|place|date|"
                    + "languages?\\s+known|mother\\s+tongue|hobbies)\\b\\.?\\s*[:\\-–]",
            Pattern.CASE_INSENSITIVE);
    /** Personal phrases wherever they appear. */
    private static final Pattern PERSONAL_ANYWHERE = Pattern.compile(
            "\\b(date of birth|marital status|religion|caste|father'?s name|mother'?s name|spouse'?s name|"
                    + "husband'?s name|i hereby declare|hereby declare that)\\b",
            Pattern.CASE_INSENSITIVE);
    /** A line that is only a personal detail, e.g. "Female" or "Married". */
    private static final Pattern PERSONAL_WORD_LINE = Pattern.compile(
            "^\\s*(male|female|married|unmarried|single|divorced|widowed|hindu|muslim|christian|sikh|jain|buddhist|parsi)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}[0-9]{4}[A-Z]\\b");
    private static final Pattern AADHAAR = Pattern.compile("\\b[2-9][0-9]{3}\\s?[0-9]{4}\\s?[0-9]{4}\\b");
    private static final Pattern PASSPORT = Pattern.compile("\\b[A-PR-WY][1-9][0-9]\\s?[0-9]{4}[1-9]\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d ()-]{8,17}\\d");
    private static final Pattern YEAR_RANGE = Pattern.compile("(19|20)\\d{2}\\s*-\\s*(19|20)\\d{2}");
    private static final Pattern LINK = Pattern.compile("(https?://|www\\.|linkedin\\.com)\\S*", Pattern.CASE_INSENSITIVE);

    public String redact(String text, CandidateDetails details) {
        String nameRegex = nameRegex(details.fullName());
        return Arrays.stream(text.split("\\R"))
                .filter(line -> !PERSONAL_LABEL.matcher(line).find())
                .filter(line -> !PERSONAL_ANYWHERE.matcher(line).find())
                .filter(line -> !PERSONAL_WORD_LINE.matcher(line).find())
                .map(line -> EMAIL.matcher(line).replaceAll(""))
                .map(line -> LINK.matcher(line).replaceAll(""))
                .map(line -> PAN.matcher(line).replaceAll(""))
                .map(line -> AADHAAR.matcher(line).replaceAll(""))
                .map(line -> PASSPORT.matcher(line).replaceAll(""))
                .map(line -> PHONE.matcher(line).replaceAll(match ->
                        YEAR_RANGE.matcher(match.group()).matches() ? match.group() : ""))
                .map(line -> nameRegex == null ? line : line.replaceAll(nameRegex, ""))
                .collect(Collectors.joining("\n"));
    }

    private static String nameRegex(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String alternatives = Arrays.stream(fullName.split("\\s+"))
                .map(part -> part.replaceAll("[^\\p{L}]", ""))
                .filter(part -> part.length() > 2)
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return alternatives.isEmpty() ? null : "(?i)(?<![\\p{L}])(" + alternatives + ")(?![\\p{L}])";
    }

    static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
