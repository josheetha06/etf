package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.screening.SkillDictionary;
import com.epictalenttrust.hr.screening.TermMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks shortlisting criteria as HR types them: tidies spelling (aws → AWS), shows what each keyword also matches,
 * blocks the same keyword in two lists, and warns about words that would screen people on who they are rather than
 * what they can do, or certifications that are really skill names.
 */
@Component
public class CriteriaAdvisor {

    /** One keyword, tidied, with what it also matches and any warning. */
    public record Item(CriterionKind kind, String value, String display, List<String> alsoMatches, String warning,
                       boolean blocking) {
    }

    private static final Map<Pattern, String> BIAS = new LinkedHashMap<>();

    static {
        bias("\\b(male|female|men|women|boys?|girls?|gents|ladies|gender)\\b",
                "Mentions gender. Choose candidates on skills, not gender.");
        bias("\\b(married|unmarried|marital|spinster|divorcee)\\b",
                "Mentions marital status, which has nothing to do with doing the job.");
        bias("\\b(age|aged|years old|young|youthful|fresh blood|below \\d{2}|under \\d{2}|upto \\d{2})\\b",
                "Mentions age. Ask for the experience you need instead.");
        bias("\\b(hindu|muslim|christian|sikh|jain|buddhist|parsi|brahmin|religion|caste|sc/st|obc)\\b",
                "Mentions religion, caste or community. These can't be used to choose candidates.");
        bias("\\b(native of|natives|locals? only|native place|hometown)\\b",
                "Mentions where someone is from. Use the job's location settings for where they need to work.");
        bias("\\b(native speaker|native english|mother tongue)\\b",
                "Mentions a native language. Ask for the language skill instead, e.g. \"Fluent English\".");
        bias("\\b(handsome|beautiful|good looking|fair complexion|fair skin|height|weight)\\b",
                "Mentions appearance, which can't be used to choose candidates.");
        bias("\\b(iit|iim|nit|bits pilani|tier ?1|top colleges?)\\b",
                "Choosing by college shuts out good candidates from other colleges. Prefer skills and experience.");
    }

    private static final Pattern CERT_WORDS = Pattern.compile(
            "\\b(certif\\w*|certificate|associate|professional|administrator|admin|developer|architect|practitioner|"
                    + "specialist|expert|foundation|engineer|consultant|master|pmp|ccna|ccnp|itil|cisa|cissp|cism|ceh|"
                    + "prince2|togaf|csm|psm|ocp|ocjp|mcsa|mcse|az-\\d+|aws-\\w+|sap\\s+\\w+)\\b",
            Pattern.CASE_INSENSITIVE);

    private static void bias(String regex, String message) {
        BIAS.put(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), message);
    }

    private final SkillDictionary dictionary;

    public CriteriaAdvisor(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public List<Item> check(java.util.UUID companyId, List<JobDtos.CriterionInput> input) {
        SkillDictionary.Vocabulary vocabulary = dictionary.vocabulary(companyId);
        Map<String, CriterionKind> firstKind = new HashMap<>();
        List<Item> items = new ArrayList<>();
        for (JobDtos.CriterionInput criterion : input) {
            String value = criterion.value() == null ? "" : criterion.value().strip();
            if (value.isEmpty()) {
                continue;
            }
            String display = criterion.kind() == CriterionKind.CERTIFICATION ? tidyCertification(value) : vocabulary.display(value);
            String key = TermMatcher.key(display);
            String warning = null;
            boolean blocking = false;
            CriterionKind seen = firstKind.putIfAbsent(key, criterion.kind());
            if (seen != null) {
                blocking = true;
                warning = seen == criterion.kind()
                        ? "\"" + display + "\" is already in this list."
                        : "\"" + display + "\" is already a " + label(seen) + ". Keep it in one list.";
            }
            if (warning == null) {
                warning = biasWarning(value);
            }
            if (warning == null && criterion.kind() == CriterionKind.CERTIFICATION && looksLikeSkill(value, vocabulary)) {
                warning = "\"" + value + "\" looks like a skill, not a certificate. Use the certificate's full name "
                        + "(e.g. \"" + vocabulary.display(value) + " Certified Administrator\") or make it a must-have.";
            }
            List<String> alsoMatches = criterion.kind() == CriterionKind.CERTIFICATION ? List.of() : vocabulary.alsoMatches(value);
            items.add(new Item(criterion.kind(), value, display, alsoMatches, warning, blocking));
        }
        return items;
    }

    public static String biasWarning(String value) {
        for (Map.Entry<Pattern, String> rule : BIAS.entrySet()) {
            if (rule.getKey().matcher(value).find()) {
                return rule.getValue();
            }
        }
        return null;
    }

    private static boolean looksLikeSkill(String value, SkillDictionary.Vocabulary vocabulary) {
        if (CERT_WORDS.matcher(value).find()) {
            return false;
        }
        return value.strip().split("\\s+").length <= 2 || vocabulary.lookup(value) != null;
    }

    private static String tidyCertification(String value) {
        String trimmed = value.strip().replaceAll("\\s+", " ");
        return trimmed.equals(trimmed.toLowerCase(Locale.ROOT)) && !trimmed.isEmpty()
                ? Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1) : trimmed;
    }

    private static String label(CriterionKind kind) {
        return switch (kind) {
            case MUST_HAVE -> "must-have";
            case NICE_TO_HAVE -> "nice-to-have";
            case CERTIFICATION -> "certification";
        };
    }
}
