package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.job.CriterionKind;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Scoring 2.0: explainable, rule-based CV scoring.
 *
 * <p>Each criterion gets credit by where the CV shows it: used in a job in the last 3 years (full credit), in an
 * older job or project (75%), only in a skills list or summary (50%), only in training, hobbies or as "basic
 * knowledge" (25%), or "no experience in …" (none). Experience counts the years in jobs that used the must-have
 * skills. Only sections the job uses count, and the total is always out of 100.
 *
 * <p>Suggestion: Shortlist needs every must-have backed by a job or project and a score at or above the client's
 * shortlist mark; Reserve needs the reserve mark; otherwise Not recommended. Messy CVs are sent to Reserve for a
 * person to check, never silently scored low.
 */
@Component
public class EvidenceScoringEngine implements ScreeningEngine {

    public static final String MODEL_VERSION = "ett-scoring-2.0";

    private static final double MUST_WEIGHT = 50;
    private static final double NICE_WEIGHT = 20;
    private static final double EXPERIENCE_WEIGHT = 20;
    private static final double CERT_WEIGHT = 10;
    private static final int RECENT_MONTHS = 36;
    private static final int QUOTE_LIMIT = 220;

    private static final Pattern NEGATION = Pattern.compile(
            "(\\b(no|zero)\\s+(prior\\s+|previous\\s+|hands[- ]on\\s+|real\\s+)?(experience|exposure|knowledge)\\b[^.;]{0,40}"
                    + "|\\b(not|never)\\s+(worked|used|familiar|exposed|experienced)\\b[^.;]{0,40}"
                    + "|\\black(?:ing)?\\s+(of\\s+)?(experience|exposure|knowledge)\\b[^.;]{0,40})$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WEAK = Pattern.compile(
            "\\b(basic knowledge|basics of|basic understanding|familiar(ity)? with|exposure to|awareness of|"
                    + "currently learning|learning|beginner|introduction to|understanding of|limited experience|"
                    + "little experience|self[- ]learn\\w*)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CERT_WORDS = Pattern.compile(
            "\\b(certif\\w*|credential\\w*|licen[cs]e\\w*|accredit\\w*)\\b", Pattern.CASE_INSENSITIVE);

    private final ExperienceEstimator experienceEstimator;

    public EvidenceScoringEngine(ExperienceEstimator experienceEstimator) {
        this.experienceEstimator = experienceEstimator;
    }

    @Override
    public String modelVersion() {
        return MODEL_VERSION;
    }

    @Override
    public ScreeningOutcome screen(ScreeningInput in) {
        YearMonth now = in.now();
        CvStructure cv = CvStructure.parse(in.redactedText(), now);
        List<List<TermMatcher.Token>> tokens = cv.lines().stream().map(l -> TermMatcher.tokens(l.text())).toList();
        int statedYears = experienceEstimator.statedYears(in.redactedText());

        List<CvStructure.Role> jobs = cv.jobs().stream().filter(r -> !r.partTime()).toList();
        int totalMonths = CvStructure.mergedMonths(jobs);
        double totalYears = jobs.isEmpty() ? statedYears : round1(totalMonths / 12.0);

        List<Found> found = new ArrayList<>();
        Map<String, Integer> termCounts = new LinkedHashMap<>();
        Set<Integer> relevantRoles = new LinkedHashSet<>();
        for (ScreeningCriterion criterion : in.criteria()) {
            List<TermMatcher.Phrase> phrases = in.vocabulary().variants(criterion.value()).stream()
                    .map(TermMatcher::phrase).filter(p -> p.size() > 0).toList();
            Found result = criterion.kind() == CriterionKind.CERTIFICATION
                    ? findCertification(cv, tokens, criterion, phrases)
                    : findSkill(cv, tokens, criterion, phrases, now);
            found.add(result);
            termCounts.put(criterion.value(), result.occurrences());
            if (criterion.kind() == CriterionKind.MUST_HAVE) {
                relevantRoles.addAll(result.workRoles());
            }
        }

        boolean hasMust = found.stream().anyMatch(f -> f.criterion().kind() == CriterionKind.MUST_HAVE);
        double relevantYears;
        if (!hasMust || cv.roles().isEmpty()) {
            relevantYears = totalYears;
        } else {
            List<CvStructure.Role> relevant = relevantRoles.stream().map(cv::role)
                    .filter(r -> !r.project() && !r.partTime()).toList();
            relevantYears = round1(CvStructure.mergedMonths(relevant) / 12.0);
        }

        // Weights: only sections the job uses, scaled so the total is 100.
        boolean useMust = hasMust;
        boolean useNice = found.stream().anyMatch(f -> f.criterion().kind() == CriterionKind.NICE_TO_HAVE);
        boolean useCert = found.stream().anyMatch(f -> f.criterion().kind() == CriterionKind.CERTIFICATION);
        boolean useExperience = in.minExperienceYears() != null && in.minExperienceYears() > 0;
        double active = (useMust ? MUST_WEIGHT : 0) + (useNice ? NICE_WEIGHT : 0) + (useCert ? CERT_WEIGHT : 0)
                + (useExperience ? EXPERIENCE_WEIGHT : 0);
        double scale = active == 0 ? 0 : 100.0 / active;

        double mustPts = sectionPoints(found, CriterionKind.MUST_HAVE, MUST_WEIGHT * scale);
        double nicePts = sectionPoints(found, CriterionKind.NICE_TO_HAVE, NICE_WEIGHT * scale);
        double certPts = sectionPoints(found, CriterionKind.CERTIFICATION, CERT_WEIGHT * scale);
        double expPts = useExperience
                ? EXPERIENCE_WEIGHT * scale * Math.min(1.0, relevantYears / in.minExperienceYears())
                : 0;
        int score = (int) Math.round(Math.min(100, mustPts + nicePts + certPts + expPts));

        List<ScreeningOutcome.CriterionResult> evidence = new ArrayList<>();
        for (Found f : found) {
            double weight = switch (f.criterion().kind()) {
                case MUST_HAVE -> MUST_WEIGHT * scale / count(found, CriterionKind.MUST_HAVE);
                case NICE_TO_HAVE -> NICE_WEIGHT * scale / count(found, CriterionKind.NICE_TO_HAVE);
                case CERTIFICATION -> CERT_WEIGHT * scale / count(found, CriterionKind.CERTIFICATION);
            };
            evidence.add(new ScreeningOutcome.CriterionResult(f.criterion().id(), f.criterion().kind(),
                    f.criterion().value(), f.strength() != EvidenceStrength.NOT_FOUND && f.strength() != EvidenceStrength.NEGATED,
                    f.quote(), f.strength(), round2(weight * f.credit()), f.section(), f.matchedTerm(), f.skillYears()));
        }

        boolean readable = readable(in.redactedText(), cv);
        List<Found> musts = found.stream().filter(f -> f.criterion().kind() == CriterionKind.MUST_HAVE).toList();
        boolean allMustBacked = musts.stream().allMatch(f -> f.strength().backedByWork() && f.yearsFactor() >= 1.0);
        boolean allMustFound = musts.stream().allMatch(f -> f.credit() > 0);

        AiSuggestion suggestion;
        if (!readable) {
            suggestion = AiSuggestion.REVIEW;
        } else if (score >= in.shortlistCutoff() && allMustBacked) {
            suggestion = AiSuggestion.SHORTLIST;
        } else if (score >= in.reserveCutoff()) {
            suggestion = AiSuggestion.REVIEW;
        } else {
            suggestion = AiSuggestion.REJECT;
        }
        String reason = reason(suggestion, readable, score, in.shortlistCutoff(), musts);
        String certificationsFound = found.stream()
                .filter(f -> f.criterion().kind() == CriterionKind.CERTIFICATION && f.credit() > 0)
                .map(f -> f.criterion().value()).collect(Collectors.joining("; "));
        String summary = summary(cv, found, totalYears, relevantYears, in.vocabulary());

        return new ScreeningOutcome(score, (int) Math.round(mustPts), (int) Math.round(nicePts),
                (int) Math.round(expPts), (int) Math.round(certPts), totalYears, relevantYears, statedYears,
                certificationsFound.isEmpty() ? null : certificationsFound, suggestion, allMustFound, reason, summary,
                readable, evidence, termCounts, cv);
    }

    // ------------------------------------------------------------------ finding skills

    private record Found(ScreeningCriterion criterion, EvidenceStrength strength, String quote, String section,
                         String matchedTerm, int occurrences, Set<Integer> workRoles, Double skillYears,
                         double yearsFactor) {

        double credit() {
            return strength.credit() * yearsFactor;
        }
    }

    private Found findSkill(CvStructure cv, List<List<TermMatcher.Token>> tokens, ScreeningCriterion criterion,
                            List<TermMatcher.Phrase> phrases, YearMonth now) {
        EvidenceStrength best = EvidenceStrength.NOT_FOUND;
        String quote = null;
        String section = null;
        String term = null;
        int occurrences = 0;
        boolean negatedSeen = false;
        String negatedQuote = null;
        Set<Integer> workRoles = new LinkedHashSet<>();
        for (CvStructure.Line line : cv.lines()) {
            for (TermMatcher.Phrase phrase : phrases) {
                Optional<int[]> at = TermMatcher.find(tokens.get(line.index()), phrase);
                if (at.isEmpty()) {
                    continue;
                }
                occurrences++;
                EvidenceStrength strength = classify(cv, line, at.get()[0], now);
                if (strength == EvidenceStrength.NEGATED) {
                    negatedSeen = true;
                    negatedQuote = quote(line.text(), at.get()[0], at.get()[1]);
                    break;
                }
                if (strength.backedByWork() && line.role() >= 0) {
                    workRoles.add(line.role());
                }
                if (rank(strength) > rank(best)) {
                    best = strength;
                    quote = quote(line.text(), at.get()[0], at.get()[1]);
                    section = sectionLabel(cv, line);
                    term = line.text().substring(at.get()[0], at.get()[1]);
                }
                break;
            }
        }
        if (best == EvidenceStrength.NOT_FOUND && negatedSeen) {
            best = EvidenceStrength.NEGATED;
            quote = negatedQuote;
        }
        Double skillYears = null;
        double yearsFactor = 1.0;
        if (!workRoles.isEmpty() || criterion.minYears() != null) {
            List<CvStructure.Role> roles = workRoles.stream().map(cv::role).filter(r -> !r.partTime()).toList();
            skillYears = round1(CvStructure.mergedMonths(roles) / 12.0);
            if (criterion.minYears() != null && criterion.minYears() > 0) {
                yearsFactor = Math.min(1.0, skillYears / criterion.minYears());
            }
        }
        return new Found(criterion, best, quote, section, term, occurrences, workRoles, skillYears, yearsFactor);
    }

    private Found findCertification(CvStructure cv, List<List<TermMatcher.Token>> tokens, ScreeningCriterion criterion,
                                    List<TermMatcher.Phrase> phrases) {
        int occurrences = 0;
        for (CvStructure.Line line : cv.lines()) {
            boolean certContext = line.section() == CvStructure.Section.CERTIFICATIONS
                    || CERT_WORDS.matcher(line.text()).find();
            for (TermMatcher.Phrase phrase : phrases) {
                List<TermMatcher.Token> lineTokens = tokens.get(line.index());
                Optional<int[]> at = TermMatcher.find(lineTokens, phrase);
                boolean allWords = phrase.size() >= 2 && TermMatcher.containsAllWords(lineTokens, phrase);
                if (at.isEmpty() && !allWords) {
                    continue;
                }
                occurrences++;
                if (certContext) {
                    int start = at.map(a -> a[0]).orElse(0);
                    int end = at.map(a -> a[1]).orElse(Math.min(line.text().length(), start + 1));
                    return new Found(criterion, EvidenceStrength.RECENT_ROLE, quote(line.text(), start, end),
                            sectionLabel(cv, line), line.text().substring(start, end), occurrences, Set.of(), null, 1.0);
                }
                break;
            }
        }
        return new Found(criterion, EvidenceStrength.NOT_FOUND, null, null, null, occurrences, Set.of(), null, 1.0);
    }

    private EvidenceStrength classify(CvStructure cv, CvStructure.Line line, int at, YearMonth now) {
        String before = line.text().substring(0, at);
        if (NEGATION.matcher(before.length() > 80 ? before.substring(before.length() - 80) : before).find()) {
            return EvidenceStrength.NEGATED;
        }
        if (WEAK.matcher(line.text()).find()) {
            return EvidenceStrength.MENTION_ONLY;
        }
        CvStructure.Role role = cv.role(line.role());
        if (role != null) {
            int monthsAgo = CvStructure.index(now) - CvStructure.index(role.end());
            return role.current() || monthsAgo <= RECENT_MONTHS ? EvidenceStrength.RECENT_ROLE : EvidenceStrength.OLDER_ROLE;
        }
        return switch (line.section()) {
            case PROJECTS -> EvidenceStrength.OLDER_ROLE;
            case HEADER, SUMMARY, SKILLS, EXPERIENCE, OTHER -> EvidenceStrength.SKILLS_ONLY;
            default -> EvidenceStrength.MENTION_ONLY;
        };
    }

    private static int rank(EvidenceStrength strength) {
        return switch (strength) {
            case RECENT_ROLE -> 5;
            case OLDER_ROLE -> 4;
            case SKILLS_ONLY -> 3;
            case MENTION_ONLY -> 2;
            case NEGATED -> 1;
            case NOT_FOUND -> 0;
        };
    }

    private static String sectionLabel(CvStructure cv, CvStructure.Line line) {
        CvStructure.Role role = cv.role(line.role());
        if (role != null) {
            return role.label();
        }
        return switch (line.section()) {
            case HEADER -> "Top of the CV";
            case SUMMARY -> "Summary";
            case SKILLS -> "Skills";
            case EXPERIENCE -> "Experience";
            case PROJECTS -> "Projects";
            case EDUCATION -> "Education";
            case CERTIFICATIONS -> "Certifications";
            case TRAINING -> "Training";
            case ACHIEVEMENTS -> "Achievements";
            case HOBBIES -> "Hobbies and interests";
            case PERSONAL -> "Personal details";
            case OTHER -> "Other";
        };
    }

    // ------------------------------------------------------------------ scoring and wording

    private static double sectionPoints(List<Found> found, CriterionKind kind, double weight) {
        List<Found> items = found.stream().filter(f -> f.criterion().kind() == kind).toList();
        if (items.isEmpty()) {
            return 0;
        }
        return weight * items.stream().mapToDouble(Found::credit).sum() / items.size();
    }

    private static long count(List<Found> found, CriterionKind kind) {
        return Math.max(1, found.stream().filter(f -> f.criterion().kind() == kind).count());
    }

    private static boolean readable(String text, CvStructure cv) {
        long letters = text.chars().filter(Character::isLetter).count();
        long visible = text.chars().filter(c -> !Character.isWhitespace(c)).count();
        return letters >= 300 && visible > 0 && (double) letters / visible >= 0.6 && cv.lines().size() >= 5;
    }

    private static String reason(AiSuggestion suggestion, boolean readable, int score, int shortlistCutoff,
                                 List<Found> musts) {
        if (!readable) {
            return "This CV couldn't be read reliably (scrambled or very short text). Check the original file.";
        }
        List<String> parts = new ArrayList<>();
        List<String> missing = musts.stream().filter(f -> f.strength() == EvidenceStrength.NOT_FOUND)
                .map(f -> f.criterion().value()).toList();
        List<String> negated = musts.stream().filter(f -> f.strength() == EvidenceStrength.NEGATED)
                .map(f -> f.criterion().value()).toList();
        List<String> skillsOnly = musts.stream()
                .filter(f -> f.strength() == EvidenceStrength.SKILLS_ONLY || f.strength() == EvidenceStrength.MENTION_ONLY)
                .map(f -> f.criterion().value()).toList();
        List<String> shortYears = musts.stream().filter(f -> f.yearsFactor() < 1.0 && f.criterion().minYears() != null)
                .map(f -> "%s: about %s of %d years asked".formatted(f.criterion().value(),
                        f.skillYears() == null ? "0" : trim(f.skillYears()), f.criterion().minYears()))
                .toList();
        if (!missing.isEmpty()) {
            parts.add("Not found: " + join(missing) + ".");
        }
        if (!negated.isEmpty()) {
            parts.add("The CV says no experience in " + join(negated) + ".");
        }
        if (!skillsOnly.isEmpty()) {
            parts.add(join(skillsOnly) + (skillsOnly.size() == 1 ? " is" : " are") + " only listed as "
                    + (skillsOnly.size() == 1 ? "a skill" : "skills") + ", not shown in any job.");
        }
        if (!shortYears.isEmpty()) {
            parts.add(String.join("; ", shortYears) + ".");
        }
        if (suggestion != AiSuggestion.SHORTLIST && score < shortlistCutoff) {
            parts.add("Score " + score + " is below the shortlist mark of " + shortlistCutoff + ".");
        }
        if (suggestion == AiSuggestion.SHORTLIST) {
            return "Every must-have is shown in a job or project, and the score is " + score + ".";
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static String summary(CvStructure cv, List<Found> found, double totalYears, double relevantYears,
                                  SkillDictionary.Vocabulary vocabulary) {
        List<String> sentences = new ArrayList<>();
        Map<Integer, List<String>> byRole = new LinkedHashMap<>();
        List<String> listed = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Found f : found) {
            if (f.criterion().kind() == CriterionKind.CERTIFICATION) {
                continue;
            }
            String name = vocabulary.display(f.criterion().value());
            if (f.strength().backedByWork() && !f.workRoles().isEmpty()) {
                int latest = f.workRoles().stream().max((a, b) -> cv.role(a).end().compareTo(cv.role(b).end())).orElse(-1);
                byRole.computeIfAbsent(latest, k -> new ArrayList<>()).add(name);
            } else if (f.strength() == EvidenceStrength.SKILLS_ONLY || f.strength() == EvidenceStrength.MENTION_ONLY) {
                listed.add(name);
            } else if (f.criterion().kind() == CriterionKind.MUST_HAVE) {
                missing.add(name);
            }
        }
        byRole.forEach((roleIndex, names) -> {
            CvStructure.Role role = cv.role(roleIndex);
            String where = role == null ? "in their work" : "in " + (role.current() ? "current job" : "a job") + " ("
                    + role.label() + ")";
            sentences.add(join(names) + " " + where + ".");
        });
        if (!listed.isEmpty()) {
            sentences.add(join(listed) + " only listed as " + (listed.size() == 1 ? "a skill" : "skills") + ".");
        }
        if (!missing.isEmpty()) {
            sentences.add("No sign of " + join(missing) + ".");
        }
        sentences.add(trim(totalYears) + " yrs total · " + trim(relevantYears) + " relevant.");
        return String.join(" ", sentences);
    }

    static String quote(String line, int start, int end) {
        if (line.length() <= QUOTE_LIMIT) {
            return line;
        }
        int middle = (start + end) / 2;
        int from = Math.max(0, middle - QUOTE_LIMIT / 2);
        int to = Math.min(line.length(), from + QUOTE_LIMIT);
        from = Math.max(0, to - QUOTE_LIMIT);
        String piece = line.substring(from, to);
        if (from > 0) {
            int space = piece.indexOf(' ');
            piece = "…" + (space > 0 && space < 25 ? piece.substring(space + 1) : piece);
        }
        if (to < line.length()) {
            int space = piece.lastIndexOf(' ');
            piece = (space > piece.length() - 25 ? piece.substring(0, space) : piece) + "…";
        }
        return piece;
    }

    private static String join(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    static String trim(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** For callers that want the counts by criterion value (keyword stuffing checks). */
    public static Map<String, Integer> counts(ScreeningOutcome outcome) {
        return outcome.termCounts() == null ? new HashMap<>() : outcome.termCounts();
    }
}
