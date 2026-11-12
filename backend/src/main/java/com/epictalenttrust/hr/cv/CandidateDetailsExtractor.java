package com.epictalenttrust.hr.cv;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Reads a candidate's name, email, phone and city from CV text. */
@Component
public class CandidateDetailsExtractor {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\d])(\\+?\\d[\\d ()-]{8,17}\\d)(?![\\d])");
    private static final Pattern NAME_WORD = Pattern.compile("\\p{Lu}[\\p{L}'.-]*");
    private static final List<String> NOT_A_NAME = List.of("curriculum", "vitae", "resume", "résumé", "profile",
            "summary", "contact", "email", "phone", "mobile", "address", "linkedin", "objective", "experience",
            "engineer", "developer", "manager", "analyst", "consultant", "architect", "skills", "education");

    private static final Map<String, String> CITIES = Map.ofEntries(
            Map.entry("Mumbai", "India"), Map.entry("Bengaluru", "India"), Map.entry("Bangalore", "India"),
            Map.entry("Chennai", "India"), Map.entry("Hyderabad", "India"), Map.entry("Pune", "India"),
            Map.entry("New Delhi", "India"), Map.entry("Delhi", "India"), Map.entry("Kolkata", "India"),
            Map.entry("Kochi", "India"), Map.entry("Cochin", "India"), Map.entry("Ahmedabad", "India"),
            Map.entry("Noida", "India"), Map.entry("Gurugram", "India"), Map.entry("Gurgaon", "India"),
            Map.entry("Coimbatore", "India"), Map.entry("Thiruvananthapuram", "India"), Map.entry("Trivandrum", "India"),
            Map.entry("Jaipur", "India"), Map.entry("Chandigarh", "India"), Map.entry("Indore", "India"),
            Map.entry("Lucknow", "India"), Map.entry("Nagpur", "India"), Map.entry("Mysuru", "India"),
            Map.entry("Mysore", "India"), Map.entry("Visakhapatnam", "India"), Map.entry("Madurai", "India"),
            Map.entry("London", "United Kingdom"), Map.entry("Manchester", "United Kingdom"),
            Map.entry("Birmingham", "United Kingdom"), Map.entry("Leeds", "United Kingdom"),
            Map.entry("Glasgow", "United Kingdom"), Map.entry("Edinburgh", "United Kingdom"),
            Map.entry("Bristol", "United Kingdom"), Map.entry("Liverpool", "United Kingdom"),
            Map.entry("Sheffield", "United Kingdom"), Map.entry("Cardiff", "United Kingdom"),
            Map.entry("Belfast", "United Kingdom"), Map.entry("Nottingham", "United Kingdom"),
            Map.entry("Leicester", "United Kingdom"), Map.entry("Newcastle", "United Kingdom"),
            Map.entry("Reading", "United Kingdom"), Map.entry("Cambridge", "United Kingdom"),
            Map.entry("Oxford", "United Kingdom"), Map.entry("Milton Keynes", "United Kingdom"),
            Map.entry("Coventry", "United Kingdom"), Map.entry("Brighton", "United Kingdom"));

    public CandidateDetails extract(String text, String fileName) {
        List<String> lines = Arrays.stream(text.split("\\R")).map(String::strip).filter(l -> !l.isEmpty()).toList();
        List<String> top = lines.subList(0, Math.min(lines.size(), 15));

        String email = firstMatch(EMAIL, text);
        String mobile = findPhone(top);
        String name = findName(top);
        if (name == null && email != null) {
            name = nameFromEmail(email);
        }
        if (name == null) {
            name = nameFromFile(fileName);
        }
        String city = null;
        String country = null;
        for (String line : top) {
            for (Map.Entry<String, String> entry : CITIES.entrySet()) {
                if (Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b").matcher(line).find()) {
                    city = canonicalCity(entry.getKey());
                    country = entry.getValue();
                    break;
                }
            }
            if (city != null) {
                break;
            }
        }
        if (country == null && mobile != null) {
            if (mobile.startsWith("+91")) {
                country = "India";
            } else if (mobile.startsWith("+44")) {
                country = "United Kingdom";
            }
        }
        return new CandidateDetails(name, email == null ? null : email.toLowerCase(Locale.ROOT), mobile, city, country);
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String findPhone(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = PHONE.matcher(line);
            while (matcher.find()) {
                String candidate = matcher.group(1).strip();
                long digits = candidate.chars().filter(Character::isDigit).count();
                if (digits >= 10 && digits <= 13 && !candidate.matches("(19|20)\\d{2}\\s*-\\s*(19|20)\\d{2}")) {
                    return candidate.replaceAll("\\s{2,}", " ");
                }
            }
        }
        return null;
    }

    private static String findName(List<String> lines) {
        for (String line : lines.subList(0, Math.min(lines.size(), 6))) {
            String clean = line.replaceAll("[|•·,]", " ").strip();
            String[] words = clean.split("\\s+");
            if (words.length < 2 || words.length > 4 || clean.length() > 40) {
                continue;
            }
            String lower = clean.toLowerCase(Locale.ROOT);
            if (NOT_A_NAME.stream().anyMatch(lower::contains) || clean.matches(".*\\d.*") || clean.contains("@")) {
                continue;
            }
            boolean allCapitalised = Arrays.stream(words).allMatch(w -> NAME_WORD.matcher(w).matches());
            if (allCapitalised) {
                return titleCase(clean);
            }
        }
        return null;
    }

    private static String nameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@')).replaceAll("\\d", "");
        String[] parts = local.split("[._-]+");
        if (parts.length < 2) {
            return null;
        }
        return titleCase(String.join(" ", parts));
    }

    private static String nameFromFile(String fileName) {
        if (fileName == null) {
            return "Unnamed candidate";
        }
        String base = fileName.replaceAll("\\.[A-Za-z0-9]+$", "")
                .replaceAll("(?i)[_\\s-]*(cv|resume|curriculum vitae)[_\\s-]*", " ")
                .replaceAll("[_.-]+", " ").strip();
        return base.isEmpty() ? "Unnamed candidate" : titleCase(base);
    }

    private static String titleCase(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(w -> !w.isEmpty())
                .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String canonicalCity(String city) {
        return switch (city) {
            case "Bangalore" -> "Bengaluru";
            case "Gurgaon" -> "Gurugram";
            case "Cochin" -> "Kochi";
            case "Trivandrum" -> "Thiruvananthapuram";
            case "Mysore" -> "Mysuru";
            default -> city;
        };
    }
}
