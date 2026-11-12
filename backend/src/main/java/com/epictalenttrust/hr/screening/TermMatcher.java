package com.epictalenttrust.hr.screening;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds skills in a line of CV text the way a recruiter reads it: ignoring case and word forms
 * ("automate", "automated", "automation"), and accepting the words of a phrase in another order
 * or with a word in between ("automation testing" also matches "test automation").
 */
public final class TermMatcher {

    /** A word in a line, with where it sits and its stem. */
    public record Token(String text, String stem, int start, int end) {
    }

    /** A skill name ready for matching. */
    public record Phrase(String raw, List<String> stems, List<String> exact, boolean caseSensitive) {
        int size() {
            return stems.size();
        }
    }

    /** Words that are also common English, matched only with the exact capitalisation given. */
    private static final Set<String> CASE_SENSITIVE = Set.of("go");

    // A token is letters/digits, keeping the symbols in names such as c++, c#, .net, node.js, ci/cd, pl/sql.
    private static final Pattern TOKEN = Pattern.compile(
            "\\.?[\\p{L}\\p{N}](?:[\\p{L}\\p{N}]|[+#]+|[./](?=[\\p{L}\\p{N}]))*");

    private static final String[][] SUFFIXES = {
            {"ations", ""}, {"ation", ""}, {"ating", ""}, {"ated", ""}, {"ates", ""}, {"ate", ""},
            {"ings", ""}, {"ing", ""}, {"ments", ""}, {"ment", ""}, {"ers", ""}, {"er", ""},
            {"ies", "y"}, {"ied", "y"}, {"ed", ""}, {"es", ""}, {"s", ""}, {"e", ""}};

    private TermMatcher() {
    }

    public static String normalise(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('–', '-').replace('—', '-').replace('‒', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace(' ', ' ')
                .replace('•', ' ').replace('●', ' ').replace('▪', ' ');
    }

    public static List<Token> tokens(String line) {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(line);
        while (matcher.find()) {
            String text = matcher.group();
            tokens.add(new Token(text, stem(text), matcher.start(), matcher.end()));
        }
        return tokens;
    }

    public static Phrase phrase(String raw) {
        List<Token> tokens = tokens(normalise(raw).strip());
        List<String> stems = tokens.stream().map(Token::stem).toList();
        List<String> exact = tokens.stream().map(Token::text).toList();
        boolean caseSensitive = tokens.size() == 1 && CASE_SENSITIVE.contains(tokens.getFirst().text().toLowerCase(Locale.ROOT));
        return new Phrase(raw, stems, exact, caseSensitive);
    }

    /**
     * Where the phrase occurs in the line, as [start, end) character offsets, or empty.
     * Multi-word phrases match when all their words appear within a short window, in any order.
     */
    public static Optional<int[]> find(List<Token> line, Phrase phrase) {
        int n = phrase.size();
        if (n == 0 || line.isEmpty()) {
            return Optional.empty();
        }
        if (n == 1) {
            for (Token token : line) {
                if (phrase.caseSensitive() ? token.text().equals(phrase.exact().getFirst())
                        : token.stem().equals(phrase.stems().getFirst())) {
                    return Optional.of(new int[] {token.start(), token.end()});
                }
            }
            return Optional.empty();
        }
        int window = n + 2;
        for (int i = 0; i < line.size(); i++) {
            if (!phrase.stems().contains(line.get(i).stem())) {
                continue;
            }
            List<String> needed = new ArrayList<>(phrase.stems());
            int last = i;
            for (int j = i; j < Math.min(line.size(), i + window) && !needed.isEmpty(); j++) {
                if (needed.remove(line.get(j).stem())) {
                    last = j;
                }
            }
            if (needed.isEmpty()) {
                return Optional.of(new int[] {line.get(i).start(), line.get(last).end()});
            }
        }
        return Optional.empty();
    }

    /** True when every significant word of the phrase is somewhere in the line (used for certification names). */
    public static boolean containsAllWords(List<Token> line, Phrase phrase) {
        List<String> stems = line.stream().map(Token::stem).toList();
        List<String> needed = phrase.stems().stream().filter(s -> !CERT_STOP_WORDS.contains(s)).toList();
        return !needed.isEmpty() && stems.containsAll(needed);
    }

    private static final Set<String> CERT_STOP_WORDS = Set.of("and", "or", "the", "of", "for", "in", "certified",
            "certification", "certificat", "certificate", "professional", "associate-level");

    /** A light stemmer: strips common English endings so word forms compare equal. Short words and names with symbols are kept. */
    public static String stem(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.length() <= 4 || !lower.chars().allMatch(Character::isLetter)) {
            return lower;
        }
        String current = lower;
        for (int pass = 0; pass < 3; pass++) {
            String next = stripOnce(current);
            if (next.equals(current)) {
                break;
            }
            current = next;
        }
        return current;
    }

    private static String stripOnce(String word) {
        for (String[] rule : SUFFIXES) {
            String suffix = rule[0];
            if (!word.endsWith(suffix)) {
                continue;
            }
            if (suffix.equals("s") && (word.endsWith("ss") || word.endsWith("us") || word.endsWith("is"))) {
                return word;
            }
            String stemmed = word.substring(0, word.length() - suffix.length()) + rule[1];
            return stemmed.length() >= 3 ? stemmed : word;
        }
        return word;
    }

    /** Lower-cased words of a phrase, for display and comparison. */
    public static String key(String phrase) {
        return String.join(" ", Arrays.stream(normalise(phrase).toLowerCase(Locale.ROOT).strip().split("\\s+")).toList());
    }
}
