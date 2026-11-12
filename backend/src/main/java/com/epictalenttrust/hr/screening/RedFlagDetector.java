package com.epictalenttrust.hr.screening;

import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rule-based checks on a CV, each with the CV lines behind it. Red flags point at possibly false information;
 * "points to check" are things worth asking about. Flags never change the score: HR decides.
 */
@Component
public class RedFlagDetector {

    /** Job limits used for the notice-period, CTC and location checks. Any may be null. */
    public record JobLimits(Integer maxNoticeDays, Double maxCtcLpa, List<String> acceptedLocations) {

        public static final JobLimits NONE = new JobLimits(null, null, List.of());
    }

    /** Client settings for gaps and short jobs. */
    public record Thresholds(int gapMonths, int shortJobMonths) {

        public static final Thresholds DEFAULT = new Thresholds(6, 12);
    }

    private static final int STUFFING_LIMIT = 15;
    private static final int RECENT_YEARS = 5;

    public List<FlagFinding> detect(ScreeningOutcome outcome, boolean hasEmail, boolean hasPhone, KeyFacts facts,
                                    JobLimits limits, Thresholds thresholds, YearMonth now) {
        List<FlagFinding> flags = new ArrayList<>();
        CvStructure cv = outcome.structure();
        List<CvStructure.Role> jobs = cv.jobs();

        // Red: dates that don't make sense.
        for (CvStructure.Role role : jobs) {
            if (role.end().isBefore(role.start())) {
                flags.add(FlagFinding.red("dates_reversed", "Job ends before it starts: " + describe(role) + ".",
                        role.dateText()));
            } else if (!role.current() && (role.end().isAfter(now) || role.start().isAfter(now))) {
                flags.add(FlagFinding.red("future_dates", "Dates in the future: " + describe(role) + ".", role.dateText()));
            }
        }

        // Red: two full-time jobs at the same time.
        List<CvStructure.Role> fullTime = jobs.stream().filter(r -> !r.partTime() && !r.end().isBefore(r.start()))
                .sorted(Comparator.comparing(CvStructure.Role::start)).toList();
        int overlaps = 0;
        for (int i = 0; i < fullTime.size() && overlaps < 2; i++) {
            for (int j = i + 1; j < fullTime.size() && overlaps < 2; j++) {
                CvStructure.Role a = fullTime.get(i);
                CvStructure.Role b = fullTime.get(j);
                int overlap = Math.min(CvStructure.index(a.end()), CvStructure.index(b.end()))
                        - Math.max(CvStructure.index(a.start()), CvStructure.index(b.start()));
                if (overlap > 2) {
                    flags.add(FlagFinding.red("overlapping_jobs", "Two full-time jobs at the same time for about "
                            + overlap + " months: " + describe(a) + " and " + describe(b) + ".", a.dateText() + " / " + b.dateText()));
                    overlaps++;
                }
            }
        }

        // Red: claimed experience well above what the dated jobs add up to.
        if (!jobs.isEmpty() && outcome.statedYears() > 0 && outcome.statedYears() - outcome.totalYears() >= 2) {
            flags.add(FlagFinding.red("experience_claim",
                    "Says " + outcome.statedYears() + " years of experience, but the dated jobs add up to about "
                            + EvidenceScoringEngine.trim(outcome.totalYears()) + ".", null));
        }

        // Red: keyword stuffing.
        for (Map.Entry<String, Integer> count : outcome.termCounts().entrySet()) {
            if (count.getValue() > STUFFING_LIMIT) {
                flags.add(FlagFinding.red("keyword_stuffing", "\"" + count.getKey() + "\" appears " + count.getValue()
                        + " times, which can be an attempt to game screening.", null));
            }
        }

        // Check: gaps between jobs, and time since the last job.
        List<int[]> spans = merged(fullTime);
        for (int i = 1; i < spans.size(); i++) {
            int gap = spans.get(i)[0] - spans.get(i - 1)[1] - 1;
            if (gap >= thresholds.gapMonths()) {
                flags.add(FlagFinding.check("employment_gap", "Gap of about " + gap + " months between jobs ("
                        + month(spans.get(i - 1)[1]) + " – " + month(spans.get(i)[0]) + ").", null));
            }
        }
        if (!spans.isEmpty() && fullTime.stream().noneMatch(CvStructure.Role::current)) {
            int since = CvStructure.index(now) - spans.getLast()[1];
            if (since >= thresholds.gapMonths()) {
                flags.add(FlagFinding.check("not_working", "Not in a job since " + month(spans.getLast()[1])
                        + " (about " + since + " months).", null));
            }
        }

        // Check: several short jobs recently.
        int recentStart = CvStructure.index(now) - RECENT_YEARS * 12;
        List<CvStructure.Role> shortJobs = fullTime.stream()
                .filter(r -> !r.current() && CvStructure.index(r.end()) >= recentStart && r.months() < thresholds.shortJobMonths())
                .toList();
        if (shortJobs.size() >= 3) {
            flags.add(FlagFinding.check("short_jobs", shortJobs.size() + " jobs shorter than " + thresholds.shortJobMonths()
                    + " months in the last " + RECENT_YEARS + " years.", shortJobs.stream().map(CvStructure.Role::label)
                    .reduce((x, y) -> x + "; " + y).orElse(null)));
        }

        // Check: missing contact details.
        if (!hasEmail) {
            flags.add(FlagFinding.check("missing_email", "No email address found in the CV.", null));
        }
        if (!hasPhone) {
            flags.add(FlagFinding.check("missing_phone", "No phone number found in the CV.", null));
        }

        // Check: outside the job's limits.
        if (facts != null) {
            if (limits.maxNoticeDays() != null && facts.noticePeriodDays() != null
                    && facts.noticePeriodDays() > limits.maxNoticeDays()) {
                flags.add(FlagFinding.check("notice_period", "Notice period " + facts.noticePeriodDays()
                        + " days (this job allows up to " + limits.maxNoticeDays() + ").", null));
            }
            Double expected = facts.expectedCtcLpa() != null ? facts.expectedCtcLpa() : facts.currentCtcLpa();
            if (limits.maxCtcLpa() != null && expected != null && expected > limits.maxCtcLpa()) {
                flags.add(FlagFinding.check("ctc_budget", (facts.expectedCtcLpa() != null ? "Expects " : "Currently earns ")
                        + EvidenceScoringEngine.trim(expected) + " LPA (budget up to "
                        + EvidenceScoringEngine.trim(limits.maxCtcLpa()) + " LPA).", null));
            }
            if (limits.acceptedLocations() != null && !limits.acceptedLocations().isEmpty() && facts.currentLocation() != null
                    && !Boolean.TRUE.equals(facts.willingToRelocate())
                    && limits.acceptedLocations().stream().noneMatch(l -> sameCity(l, facts.currentLocation()))
                    && (facts.preferredLocations() == null || facts.preferredLocations().stream()
                    .noneMatch(p -> limits.acceptedLocations().stream().anyMatch(l -> sameCity(l, p))))) {
                flags.add(FlagFinding.check("location", "Based in " + facts.currentLocation()
                        + ", not one of this job's locations (" + String.join(", ", limits.acceptedLocations())
                        + "), and doesn't say they'll relocate.", null));
            }
        }

        // Check: unreliable text.
        if (!outcome.readable()) {
            flags.add(FlagFinding.check("unreadable", "The CV text couldn't be read reliably (scrambled or very short). "
                    + "Check the original file.", null));
        }
        return flags;
    }

    private static List<int[]> merged(List<CvStructure.Role> roles) {
        List<int[]> spans = roles.stream()
                .map(r -> new int[] {CvStructure.index(r.start()), CvStructure.index(r.end())})
                .sorted(Comparator.comparingInt(s -> s[0])).toList();
        List<int[]> out = new ArrayList<>();
        for (int[] span : spans) {
            if (!out.isEmpty() && span[0] <= out.getLast()[1] + 1) {
                out.getLast()[1] = Math.max(out.getLast()[1], span[1]);
            } else {
                out.add(new int[] {span[0], span[1]});
            }
        }
        return out;
    }

    private static String month(int index) {
        return CvStructure.MONTH_YEAR.format(YearMonth.of(index / 12, index % 12 + 1));
    }

    private static String describe(CvStructure.Role role) {
        return role.title() == null || role.title().isBlank() ? role.dateText() : role.title() + " (" + role.dateText() + ")";
    }

    private static boolean sameCity(String a, String b) {
        String x = a.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        String y = b.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return !x.isEmpty() && (x.equals(y) || alias(x).equals(alias(y)));
    }

    private static String alias(String city) {
        return switch (city) {
            case "bangalore", "bengaluru", "blr" -> "bengaluru";
            case "bombay", "mumbai", "navimumbai" -> "mumbai";
            case "madras", "chennai" -> "chennai";
            case "calcutta", "kolkata" -> "kolkata";
            case "gurgaon", "gurugram" -> "gurugram";
            case "delhi", "newdelhi", "ncr", "delhincr" -> "delhi";
            case "trivandrum", "thiruvananthapuram" -> "thiruvananthapuram";
            case "cochin", "kochi" -> "kochi";
            case "poona", "pune" -> "pune";
            default -> city;
        };
    }
}
