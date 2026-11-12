package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.screening.SkillDictionary;
import com.epictalenttrust.hr.screening.TermMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Suggests must-have and nice-to-have skills from a job description. It finds the skills the portal knows (and the
 * company's own) and sorts them by the section they appear in: "Requirements", "Must have", "Essential" and similar
 * are must-haves; "Nice to have", "Preferred", "Desirable", "Bonus" are nice-to-haves. HR confirms every suggestion.
 */
@Component
public class JobDescriptionReader {

    public record Suggestions(List<String> mustHave, List<String> niceToHave) {
    }

    private static final Pattern NICE = Pattern.compile(
            "nice[ -]to[ -]have|good[ -]to[ -]have|preferred|desirable|bonus|advantage|added plus|is a plus|optional");
    private static final Pattern MUST = Pattern.compile(
            "must[ -]have|required|requirements|essential|mandatory|qualifications|what you.ll need|you have|you bring|skills");
    private static final int MAX_MUST = 8;
    private static final int MAX_NICE = 6;

    private final SkillDictionary dictionary;

    public JobDescriptionReader(SkillDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public Suggestions suggest(UUID companyId, String text) {
        if (text == null || text.isBlank()) {
            return new Suggestions(List.of(), List.of());
        }
        SkillDictionary.Vocabulary vocabulary = dictionary.vocabulary(companyId);
        List<SkillDictionary.Entry> entries = new ArrayList<>(vocabulary.entries());
        List<List<TermMatcher.Phrase>> phrases = entries.stream()
                .map(e -> e.names().stream().map(TermMatcher::phrase).toList()).toList();
        Map<String, Boolean> found = new LinkedHashMap<>();   // skill → seen in a must-have section
        boolean inNice = false;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            String lower = line.toLowerCase(Locale.ROOT);
            boolean heading = line.length() <= 60 || line.endsWith(":") || lower.matches("^[#*\\-\\s]*[a-z ',/&]+:.*");
            if (heading && NICE.matcher(lower).find()) {
                inNice = true;
            } else if (heading && MUST.matcher(lower).find()) {
                inNice = false;
            }
            boolean niceLine = inNice || NICE.matcher(lower).find();
            List<TermMatcher.Token> tokens = TermMatcher.tokens(line);
            for (int i = 0; i < entries.size(); i++) {
                for (TermMatcher.Phrase phrase : phrases.get(i)) {
                    if (TermMatcher.find(tokens, phrase).isPresent()) {
                        String skill = entries.get(i).display();
                        found.merge(skill, !niceLine, (was, now) -> was || now);
                        break;
                    }
                }
            }
        }
        List<String> must = found.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
                .limit(MAX_MUST).toList();
        List<String> nice = found.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey)
                .filter(s -> !must.contains(s)).limit(MAX_NICE).toList();
        return new Suggestions(must, nice);
    }
}
