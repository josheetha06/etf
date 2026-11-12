package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.screening.SkillDictionary;
import com.epictalenttrust.hr.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Per-client shortlisting settings: cut-offs, red-flag thresholds, extra skill names and how well the AI is doing. */
@Service
public class ScreeningSettingsService {

    public record Thresholds(@NotNull @Min(1) @Max(100) Integer shortlistCutoff,
                             @NotNull @Min(0) @Max(99) Integer reserveCutoff,
                             @NotNull @Min(1) @Max(60) Integer gapFlagMonths,
                             @NotNull @Min(1) @Max(60) Integer shortJobMonths) {
    }

    public record SkillInput(@NotBlank @Size(max = 80) String skill,
                             @NotNull @Size(max = 30) List<@NotBlank @Size(max = 80) String> otherNames) {
    }

    public record SkillView(UUID id, String skill, List<String> otherNames, boolean custom, List<String> alsoCounts) {
    }

    /** How often HR agreed with the AI, and how the candidates it suggested did later. */
    public record Accuracy(int screened, int decided, int agreed, Integer agreementPct, List<SuggestionRow> bySuggestion,
                           List<BandRow> byScore, int shortlistCutoff, int reserveCutoff) {
    }

    public record SuggestionRow(String suggestion, int screened, int hrShortlisted, int hrRejected, int hrHold,
                                int passedRound1) {
    }

    public record BandRow(String band, int from, int to, int screened, int hrShortlisted, int passedRound1) {
    }

    private final CompanySettingsRepository settings;
    private final SkillDictionary dictionary;
    private final ActivityService activity;
    private final Tenancy tenancy;
    private final JdbcClient jdbc;

    public ScreeningSettingsService(CompanySettingsRepository settings, SkillDictionary dictionary,
                                    ActivityService activity, Tenancy tenancy, JdbcClient jdbc) {
        this.settings = settings;
        this.dictionary = dictionary;
        this.activity = activity;
        this.tenancy = tenancy;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Thresholds thresholds() {
        return settings.findByCompanyId(CurrentUser.companyId())
                .map(s -> new Thresholds(s.getShortlistCutoff(), s.getReserveCutoff(), s.getGapFlagMonths(),
                        s.getShortJobMonths()))
                .orElse(new Thresholds(CompanySettings.DEFAULT_SHORTLIST, CompanySettings.DEFAULT_RESERVE, 6, 12));
    }

    /** New cut-offs apply to CVs screened from now on; earlier results keep the cut-offs they were scored with. */
    @Transactional
    public Thresholds saveThresholds(Thresholds input) {
        if (input.reserveCutoff() >= input.shortlistCutoff()) {
            throw ApiException.badRequest("cutoffs_order", "The reserve cut-off must be lower than the shortlist cut-off.");
        }
        CompanySettings current = settings.findByCompanyId(CurrentUser.companyId())
                .orElseGet(() -> settings.save(new CompanySettings(CurrentUser.companyId())));
        current.update(input.shortlistCutoff(), input.reserveCutoff(), input.gapFlagMonths(), input.shortJobMonths());
        activity.record("settings.screening_updated", "company", CurrentUser.companyId());
        return thresholds();
    }

    @Transactional(readOnly = true)
    public List<SkillView> skills(String query) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<SkillView> out = new ArrayList<>();
        jdbc.sql("select id, skill, synonyms from skill_synonyms where company_id = :c order by lower(skill)")
                .param("c", CurrentUser.companyId())
                .query((rs, row) -> new SkillView(Rows.uuid(rs, "id"), rs.getString("skill"),
                        textArray(rs.getArray("synonyms")), true, List.of()))
                .list().stream().filter(s -> matches(s, q)).forEach(out::add);
        dictionary.builtInEntries().stream()
                .map(e -> new SkillView(null, e.display(),
                        e.names().stream().filter(n -> !n.equalsIgnoreCase(e.display())).toList(), false, e.alsoCounts()))
                .filter(s -> matches(s, q))
                .limit(q.isEmpty() ? 400 : 60)
                .forEach(out::add);
        return out;
    }

    @Transactional
    public SkillView addSkill(SkillInput input) {
        String skill = input.skill().strip();
        List<String> names = tidy(input.otherNames(), skill);
        boolean exists = jdbc.sql("select exists (select 1 from skill_synonyms where company_id = :c and lower(skill) = lower(:s))")
                .param("c", CurrentUser.companyId()).param("s", skill).query(Boolean.class).single();
        if (exists) {
            throw ApiException.conflict("skill_exists", "\"" + skill + "\" is already in your list. Edit it instead.");
        }
        UUID id = jdbc.sql("""
                        insert into skill_synonyms (company_id, skill, synonyms, created_by, last_modified_by)
                        values (:c, :s, :n, :u, :u) returning id
                        """)
                .param("c", CurrentUser.companyId()).param("s", skill).param("n", names.toArray(String[]::new))
                .param("u", CurrentUser.id()).query(UUID.class).single();
        activity.record("settings.skill_added", "company", CurrentUser.companyId());
        return new SkillView(id, skill, names, true, List.of());
    }

    @Transactional
    public SkillView updateSkill(UUID id, SkillInput input) {
        String skill = input.skill().strip();
        List<String> names = tidy(input.otherNames(), skill);
        int updated = jdbc.sql("""
                        update skill_synonyms set skill = :s, synonyms = :n, last_modified_by = :u
                         where id = :id and company_id = :c
                        """)
                .param("s", skill).param("n", names.toArray(String[]::new)).param("u", CurrentUser.id())
                .param("id", id).param("c", CurrentUser.companyId()).update();
        if (updated == 0) {
            throw ApiException.notFound("Skill");
        }
        activity.record("settings.skill_updated", "company", CurrentUser.companyId());
        return new SkillView(id, skill, names, true, List.of());
    }

    @Transactional
    public void deleteSkill(UUID id) {
        int deleted = jdbc.sql("delete from skill_synonyms where id = :id and company_id = :c")
                .param("id", id).param("c", CurrentUser.companyId()).update();
        if (deleted == 0) {
            throw ApiException.notFound("Skill");
        }
        activity.record("settings.skill_removed", "company", CurrentUser.companyId());
    }

    @Transactional(readOnly = true)
    public Accuracy accuracy(UUID jobId) {
        if (jobId != null) {
            tenancy.job(jobId);
        }
        Thresholds thresholds = thresholds();
        String base = """
                with latest as (
                  select a.id as application_id, s.suggestion, s.match_score,
                         (select decision from shortlist_decisions d where d.application_id = a.id
                           order by created_at desc limit 1) as decision,
                         exists (select 1 from stage_decisions sd where sd.application_id = a.id and sd.stage = 'round1'
                                    and sd.decision = 'advance') as passed_r1
                    from applications a join jobs j on j.id = a.job_id
                    join lateral (select suggestion, match_score from ai_screenings where application_id = a.id
                                   order by created_at desc limit 1) s on true
                   where j.company_id = :c and (cast(:job as uuid) is null or a.job_id = :job)
                )
                """;
        List<SuggestionRow> bySuggestion = jdbc.sql(base + """
                        select suggestion::text as suggestion, count(*) as n,
                               count(*) filter (where decision = 'shortlisted') as sl,
                               count(*) filter (where decision = 'rejected') as rj,
                               count(*) filter (where decision = 'hold') as hd,
                               count(*) filter (where passed_r1) as p1
                          from latest group by suggestion order by suggestion
                        """)
                .param("c", CurrentUser.companyId()).param("job", jobId)
                .query((rs, row) -> new SuggestionRow(rs.getString("suggestion"), rs.getInt("n"), rs.getInt("sl"),
                        rs.getInt("rj"), rs.getInt("hd"), rs.getInt("p1")))
                .list();
        List<BandRow> byScore = jdbc.sql(base + """
                        select b.band, b.lo, b.hi, count(l.application_id) as n,
                               count(l.application_id) filter (where l.decision = 'shortlisted') as sl,
                               count(l.application_id) filter (where l.passed_r1) as p1
                          from (values ('90–100', 90, 100), ('80–89', 80, 89), ('70–79', 70, 79), ('60–69', 60, 69),
                                       ('50–59', 50, 59), ('Below 50', 0, 49)) as b(band, lo, hi)
                          left join latest l on l.match_score between b.lo and b.hi
                         group by b.band, b.lo, b.hi order by b.lo desc
                        """)
                .param("c", CurrentUser.companyId()).param("job", jobId)
                .query((rs, row) -> new BandRow(rs.getString("band"), rs.getInt("lo"), rs.getInt("hi"), rs.getInt("n"),
                        rs.getInt("sl"), rs.getInt("p1")))
                .list();
        record Totals(int screened, int decided, int agreed) {
        }
        Totals totals = jdbc.sql(base + """
                        select count(*) as n, count(decision) filter (where decision <> 'hold') as decided,
                               count(*) filter (where (suggestion = 'shortlist' and decision = 'shortlisted')
                                                   or (suggestion = 'reject' and decision = 'rejected')) as agreed
                          from latest
                        """)
                .param("c", CurrentUser.companyId()).param("job", jobId)
                .query((rs, row) -> new Totals(rs.getInt("n"), rs.getInt("decided"), rs.getInt("agreed"))).single();
        Integer pct = totals.decided() == 0 ? null : (int) Math.round(100.0 * totals.agreed() / totals.decided());
        return new Accuracy(totals.screened(), totals.decided(), totals.agreed(), pct, bySuggestion, byScore,
                thresholds.shortlistCutoff(), thresholds.reserveCutoff());
    }

    private static boolean matches(SkillView skill, String q) {
        return q.isEmpty() || skill.skill().toLowerCase(Locale.ROOT).contains(q)
                || skill.otherNames().stream().anyMatch(n -> n.toLowerCase(Locale.ROOT).contains(q));
    }

    private static List<String> tidy(List<String> names, String skill) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String name : names) {
            String n = name.strip().replaceAll("\\s+", " ");
            if (!n.isEmpty() && !n.equalsIgnoreCase(skill)
                    && out.stream().noneMatch(existing -> existing.equalsIgnoreCase(n))) {
                out.add(n);
            }
        }
        if (out.isEmpty()) {
            throw ApiException.badRequest("names_required", "Add at least one other name for \"" + skill + "\".");
        }
        return List.copyOf(out);
    }

    private static List<String> textArray(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return java.util.Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
