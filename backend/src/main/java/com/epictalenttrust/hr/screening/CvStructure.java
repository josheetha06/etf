package com.epictalenttrust.hr.screening;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shape of a CV: which section each line is in, and the jobs (and dated projects) with their dates.
 * Built from plain extracted text, so it copes with headings in capitals, "Skills: …" lines and CVs with no headings.
 */
public final class CvStructure {

    public enum Section {
        HEADER, SUMMARY, SKILLS, EXPERIENCE, PROJECTS, EDUCATION, CERTIFICATIONS, TRAINING, ACHIEVEMENTS, HOBBIES,
        PERSONAL, OTHER
    }

    /** One non-empty line of the CV. {@code role} is the index into {@link #roles()}, or -1. */
    public record Line(int index, String text, Section section, int role) {
    }

    /** A job or dated project. {@code end} is the current month when {@code current}. */
    public record Role(int index, String title, YearMonth start, YearMonth end, boolean current, boolean partTime,
                       boolean project, String dateText, int firstLine, int lastLine) {

        public int months() {
            return Math.max(0, (end.getYear() * 12 + end.getMonthValue()) - (start.getYear() * 12 + start.getMonthValue()) + 1);
        }

        public String label() {
            String period = MONTH_YEAR.format(start) + " – " + (current ? "now" : MONTH_YEAR.format(end));
            return (title == null || title.isBlank() ? "Job" : title) + " (" + period + ")";
        }
    }

    static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private static final String MONTH = "(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?";
    private static final String DATE = "(?:" + MONTH + "\\s*['’]?\\s*)?((?:19|20)\\d{2})|(\\d{1,2})\\s*[/.-]\\s*((?:19|20)\\d{2})";
    // start: month-name year | year | mm/yyyy ; end: same or present
    private static final Pattern RANGE = Pattern.compile(
            "(?:" + DATE + ")\\s*(?:-|–|to|till|until)\\s*(?:(?:" + DATE + ")|(present|current|now|date|today|till date|to date))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PART_TIME = Pattern.compile(
            "\\b(intern|internship|trainee|part[- ]time|freelance|freelancer|volunteer|apprentice)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EDUCATION_LINE = Pattern.compile(
            "\\b(b\\.?\\s?tech|m\\.?\\s?tech|b\\.e\\b|m\\.e\\b|b\\.?sc|m\\.?sc|bca|mca|mba|ph\\.?d|bachelor|master'?s|degree|university|college|school|diploma|graduat|class (x|xii|10|12)|ssc|hsc|cbse|icse)",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Section> HEADINGS = new LinkedHashMap<>();

    static {
        heading(Section.SUMMARY, "summary", "professional summary", "profile", "profile summary", "career objective",
                "objective", "about me", "career summary", "overview", "professional profile", "executive summary");
        heading(Section.SKILLS, "skills", "technical skills", "key skills", "core skills", "skill set", "skillset",
                "core competencies", "competencies", "technologies", "tools", "tools and technologies", "tech stack",
                "technical expertise", "areas of expertise", "expertise", "it skills", "software skills");
        heading(Section.EXPERIENCE, "experience", "work experience", "professional experience", "employment",
                "employment history", "work history", "career history", "career", "experience summary",
                "relevant experience", "professional background", "organisational experience", "organizational experience");
        heading(Section.PROJECTS, "projects", "key projects", "project experience", "project details",
                "academic projects", "personal projects", "projects handled");
        heading(Section.EDUCATION, "education", "academic", "academics", "qualifications", "educational qualifications",
                "academic qualifications", "academic details", "educational background", "education and training");
        heading(Section.CERTIFICATIONS, "certifications", "certificates", "certification", "licenses", "licences",
                "credentials", "certifications and training", "professional certifications");
        heading(Section.TRAINING, "training", "trainings", "courses", "coursework", "workshops", "learning",
                "online courses");
        heading(Section.ACHIEVEMENTS, "achievements", "awards", "accomplishments", "honours", "honors",
                "awards and achievements", "recognition");
        heading(Section.HOBBIES, "hobbies", "interests", "extracurricular", "extra-curricular activities",
                "extracurricular activities", "activities", "hobbies and interests");
        heading(Section.PERSONAL, "personal details", "personal information", "personal profile", "declaration",
                "languages", "languages known", "personal data", "references");
    }

    private static void heading(Section section, String... names) {
        for (String name : names) {
            HEADINGS.put(name, section);
        }
    }

    private final List<Line> lines;
    private final List<Role> roles;
    private final boolean hasHeadings;

    private CvStructure(List<Line> lines, List<Role> roles, boolean hasHeadings) {
        this.lines = lines;
        this.roles = roles;
        this.hasHeadings = hasHeadings;
    }

    public List<Line> lines() {
        return lines;
    }

    public List<Role> roles() {
        return roles;
    }

    public boolean hasHeadings() {
        return hasHeadings;
    }

    public Role role(int index) {
        return index < 0 ? null : roles.get(index);
    }

    /** Work roles only (no internships or projects), for experience totals, gaps and overlaps. */
    public List<Role> jobs() {
        return roles.stream().filter(r -> !r.project()).toList();
    }

    public static CvStructure parse(String rawText, YearMonth now) {
        String text = TermMatcher.normalise(rawText);
        List<String> raw = text.lines().map(String::strip).filter(l -> !l.isEmpty()).toList();

        // 1. Sections.
        List<Section> sections = new ArrayList<>();
        List<String> content = new ArrayList<>();
        Section current = Section.HEADER;
        boolean anyHeading = false;
        for (String line : raw) {
            HeadingMatch match = matchHeading(line);
            if (match != null) {
                current = match.section();
                anyHeading = true;
                if (match.rest().isBlank()) {
                    continue;
                }
                sections.add(current);
                content.add(match.rest());
                continue;
            }
            sections.add(current);
            content.add(line);
        }

        // 2. Roles: date ranges inside experience or projects (anywhere outside education when there are no headings).
        List<Line> lines = new ArrayList<>();
        List<RoleDraft> drafts = new ArrayList<>();
        int[] roleOf = new int[content.size()];
        java.util.Arrays.fill(roleOf, -1);
        for (int i = 0; i < content.size(); i++) {
            String line = content.get(i);
            Section section = sections.get(i);
            boolean roleSection = section == Section.EXPERIENCE || section == Section.PROJECTS
                    || (!anyHeading && section == Section.HEADER && !EDUCATION_LINE.matcher(line).find());
            Matcher range = RANGE.matcher(line);
            if (roleSection && range.find()) {
                Dates dates = dates(range, now);
                if (dates != null) {
                    String titleHere = cleanTitle(line.substring(0, range.start()) + " " + line.substring(range.end()));
                    int titleLine = i;
                    String title = titleHere;
                    String above = i > 0 ? content.get(i - 1) : "";
                    if (titleHere.length() < 3 && i > 0 && sections.get(i - 1) == section && above.length() < 100
                            && !above.endsWith(".") && !RANGE.matcher(above).find()) {
                        // The title is the line above the dates: move it from the previous role to this one.
                        title = cleanTitle(content.get(i - 1));
                        titleLine = i - 1;
                    }
                    boolean partTime = PART_TIME.matcher(line).find() || PART_TIME.matcher(title).find();
                    drafts.add(new RoleDraft(drafts.size(), title, dates, partTime || section == Section.PROJECTS,
                            section == Section.PROJECTS, range.group(), titleLine));
                    for (int k = titleLine; k <= i; k++) {
                        roleOf[k] = drafts.size() - 1;
                    }
                    continue;
                }
            }
            if (roleSection && !drafts.isEmpty() && roleOf[i] == -1) {
                RoleDraft last = drafts.getLast();
                if (sections.get(last.titleLine()) == section) {
                    roleOf[i] = last.index();
                }
            }
        }

        List<Role> roles = new ArrayList<>();
        for (RoleDraft draft : drafts) {
            int first = -1;
            int lastLine = -1;
            for (int i = 0; i < roleOf.length; i++) {
                if (roleOf[i] == draft.index()) {
                    first = first < 0 ? i : first;
                    lastLine = i;
                }
            }
            roles.add(new Role(draft.index(), draft.title(), draft.dates().start(), draft.dates().end(),
                    draft.dates().current(), draft.partTime(), draft.project(), draft.dateText(), first, lastLine));
        }
        for (int i = 0; i < content.size(); i++) {
            Section section = sections.get(i);
            if (!anyHeading && roleOf[i] >= 0) {
                section = Section.EXPERIENCE;
            }
            lines.add(new Line(i, content.get(i), section, roleOf[i]));
        }
        return new CvStructure(List.copyOf(lines), List.copyOf(roles), anyHeading);
    }

    /** Total months covered by the given roles, counting overlaps once. */
    public static int mergedMonths(List<Role> roles) {
        List<int[]> spans = roles.stream()
                .map(r -> new int[] {index(r.start()), index(r.end())})
                .filter(s -> s[1] >= s[0])
                .sorted(Comparator.comparingInt(s -> s[0]))
                .toList();
        int total = 0;
        int start = -1;
        int end = -2;
        for (int[] span : spans) {
            if (span[0] > end + 1) {
                if (end >= start && start >= 0) {
                    total += end - start + 1;
                }
                start = span[0];
                end = span[1];
            } else {
                end = Math.max(end, span[1]);
            }
        }
        if (end >= start && start >= 0) {
            total += end - start + 1;
        }
        return total;
    }

    static int index(YearMonth month) {
        return month.getYear() * 12 + month.getMonthValue() - 1;
    }

    private record HeadingMatch(Section section, String rest) {
    }

    private record Dates(YearMonth start, YearMonth end, boolean current) {
    }

    private record RoleDraft(int index, String title, Dates dates, boolean partTime, boolean project, String dateText,
                             int titleLine) {
    }

    private static HeadingMatch matchHeading(String line) {
        if (line.length() > 120) {
            return null;
        }
        String head = line;
        String rest = "";
        int colon = line.indexOf(':');
        if (colon > 0 && colon <= 40) {
            head = line.substring(0, colon);
            rest = line.substring(colon + 1).strip();
        } else if (line.length() > 45) {
            return null;
        }
        String key = head.toLowerCase(Locale.ROOT).replaceAll("[^a-z &-]", " ").replace("&", "and")
                .replaceAll("\\s+", " ").strip();
        Section section = HEADINGS.get(key);
        if (section == null) {
            return null;
        }
        // "Experience: 6 years" is a fact, not a heading, unless the rest looks like a list.
        if (section == Section.EXPERIENCE && !rest.isEmpty()) {
            return null;
        }
        return new HeadingMatch(section, rest);
    }

    private static Dates dates(Matcher m, YearMonth now) {
        YearMonth start = date(m.group(1), m.group(2), m.group(3), m.group(4), 1);
        if (start == null) {
            return null;
        }
        boolean current = m.group(9) != null;
        YearMonth end = current ? now : date(m.group(5), m.group(6), m.group(7), m.group(8), 12);
        if (end == null) {
            return null;
        }
        return new Dates(start, end, current);
    }

    private static YearMonth date(String monthName, String year, String monthNumber, String yearAfterNumber, int defaultMonth) {
        try {
            if (year != null) {
                int month = monthName == null ? defaultMonth : monthOf(monthName);
                return YearMonth.of(Integer.parseInt(year), month);
            }
            if (monthNumber != null && yearAfterNumber != null) {
                int month = Integer.parseInt(monthNumber);
                if (month < 1 || month > 12) {
                    return null;
                }
                return YearMonth.of(Integer.parseInt(yearAfterNumber), month);
            }
        } catch (RuntimeException ex) {
            return null;
        }
        return null;
    }

    private static int monthOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT).substring(0, 3)) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            default -> 12;
        };
    }

    private static String cleanTitle(String text) {
        return text.replaceAll("[|()\\[\\]]", " ").replaceAll("\\s*[-–,:]\\s*$", "").replaceAll("^\\s*[-–,:]\\s*", "")
                .replaceAll("\\s+", " ").strip();
    }
}
