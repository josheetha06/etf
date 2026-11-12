package com.epictalenttrust.hr.screening;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Skills and their other names: the built-in list (resources/skills/dictionary.txt) plus each client's own
 * additions (table skill_synonyms). Any name of a skill finds the whole entry.
 */
@Component
public class SkillDictionary {

    /**
     * A skill: how to display it, every name it goes by (display name first), and things that also count as
     * evidence of it (one way only: EC2 shows AWS, AWS doesn't show EC2).
     */
    public record Entry(String display, List<String> names, List<String> alsoCounts, boolean custom) {

        Entry(String display, List<String> names, boolean custom) {
            this(display, names, List.of(), custom);
        }
    }

    private final Map<String, Entry> builtIn;
    private final JdbcClient jdbc;

    public SkillDictionary(JdbcClient jdbc) {
        this.jdbc = jdbc;
        this.builtIn = load();
    }

    /** A snapshot for one client, to use for a whole screening run. */
    public Vocabulary vocabulary(UUID companyId) {
        Map<String, Entry> merged = new HashMap<>(builtIn);
        if (companyId != null) {
            for (Entry entry : companyEntries(companyId)) {
                for (String name : entry.names()) {
                    merged.put(TermMatcher.key(name), entry);
                }
            }
        }
        return new Vocabulary(merged);
    }

    /** The built-in skills, one entry each, for the Settings page. */
    public List<Entry> builtInEntries() {
        return builtIn.values().stream().distinct()
                .sorted(java.util.Comparator.comparing(e -> e.display().toLowerCase(java.util.Locale.ROOT))).toList();
    }

    public List<Entry> companyEntries(UUID companyId) {
        return jdbc.sql("select skill, synonyms from skill_synonyms where company_id = :c order by lower(skill)")
                .param("c", companyId)
                .query((rs, row) -> {
                    List<String> names = new ArrayList<>();
                    names.add(rs.getString("skill"));
                    names.addAll(textArray(rs.getArray("synonyms")));
                    return new Entry(rs.getString("skill"), List.copyOf(new LinkedHashSet<>(names)), true);
                })
                .list();
    }

    /** The skills one client sees: built-in entries, with their own additions on top. */
    public static final class Vocabulary {

        private final Map<String, Entry> byName;

        Vocabulary(Map<String, Entry> byName) {
            this.byName = byName;
        }

        public Entry lookup(String term) {
            return term == null ? null : byName.get(TermMatcher.key(term));
        }

        /** Every skill once, for finding skills in a job description. */
        public java.util.Collection<Entry> entries() {
            return new java.util.LinkedHashSet<>(byName.values());
        }

        /** The term and every other name for it. */
        public List<String> variants(String term) {
            Set<String> names = new LinkedHashSet<>();
            names.add(term.strip());
            Entry entry = lookup(term);
            if (entry != null) {
                names.addAll(entry.names());
                names.addAll(entry.alsoCounts());
            }
            return List.copyOf(names);
        }

        /** Tidy display: the dictionary's spelling when known (aws → AWS), otherwise the first letter in capitals. */
        public String display(String term) {
            Entry entry = lookup(term);
            if (entry != null) {
                return entry.display();
            }
            String trimmed = term.strip().replaceAll("\\s+", " ");
            if (trimmed.isEmpty() || !trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
                return trimmed;
            }
            return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        }

        /** Other names a term also matches, for showing under a keyword. */
        public List<String> alsoMatches(String term) {
            Entry entry = lookup(term);
            if (entry == null) {
                return List.of();
            }
            String key = TermMatcher.key(term);
            List<String> all = new ArrayList<>(entry.names());
            all.addAll(entry.alsoCounts());
            return all.stream().filter(n -> !TermMatcher.key(n).equals(key)).toList();
        }
    }

    private static Map<String, Entry> load() {
        Map<String, Entry> entries = new HashMap<>();
        var resource = new ClassPathResource("skills/dictionary.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(">", 2);
                List<String> names = Arrays.stream(parts[0].split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList();
                List<String> alsoCounts = parts.length < 2 ? List.of()
                        : Arrays.stream(parts[1].split("\\|")).map(String::strip).filter(s -> !s.isEmpty()).toList();
                Entry entry = new Entry(names.getFirst(), names, alsoCounts, false);
                for (String name : names) {
                    entries.putIfAbsent(TermMatcher.key(name), entry);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read the skills dictionary", ex);
        }
        return Map.copyOf(entries);
    }

    private static List<String> textArray(Array array) {
        if (array == null) {
            return List.of();
        }
        try {
            Object[] values = (Object[]) array.getArray();
            return Arrays.stream(values).map(String::valueOf).toList();
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
