package com.epictalenttrust.hr.settings;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.JobDtos.RoundInput;
import com.epictalenttrust.hr.job.JobDtos.RoundView;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Settings HR admins change for their company: details, the default round plan, review measures, email wording. */
@Service
public class CompanySettingsService {

    public record CompanyDetails(String name, String address, String timeZone, String country) {
    }

    public record CompanyInput(@NotBlank @Size(max = 120) String name, @Size(max = 500) String address,
                               @NotBlank @Size(max = 60) String timeZone) {
    }

    public record EmailTemplateView(String key, String label, String description, List<String> placeholders,
                                    String subject, String body, boolean customised, String defaultSubject,
                                    String defaultBody) {
    }

    public record EmailTemplateInput(@NotBlank @Size(max = 200) String subject, @NotBlank @Size(max = 8000) String body) {
    }

    public record Preview(String subject, String body) {
    }

    private final JdbcClient jdbc;
    private final MessageTemplates templates;
    private final ActivityService activity;

    public CompanySettingsService(JdbcClient jdbc, MessageTemplates templates, ActivityService activity) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.activity = activity;
    }

    // ------------------------------------------------------------------ company details

    @Transactional(readOnly = true)
    public CompanyDetails company() {
        return jdbc.sql("select name, address, time_zone, country from companies where id = :c")
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> new CompanyDetails(rs.getString("name"), rs.getString("address"),
                        rs.getString("time_zone"), rs.getString("country")))
                .single();
    }

    @Transactional
    public CompanyDetails saveCompany(CompanyInput input) {
        try {
            ZoneId.of(input.timeZone().strip());
        } catch (RuntimeException ex) {
            throw ApiException.badRequest("invalid_time_zone", "Choose a time zone from the list.");
        }
        jdbc.sql("update companies set name = :n, address = :a, time_zone = :z where id = :c")
                .param("n", input.name().strip())
                .param("a", input.address() == null || input.address().isBlank() ? null : input.address().strip())
                .param("z", input.timeZone().strip()).param("c", CurrentUser.companyId()).update();
        activity.record("settings.company_changed", "company", CurrentUser.companyId());
        return company();
    }

    // ------------------------------------------------------------------ default round plan

    @Transactional(readOnly = true)
    public List<RoundView> roundPlan() {
        return jdbc.sql("""
                        select round, name, kind, mode, duration_min, target_days from round_templates
                         where company_id = :c order by round
                        """)
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> new RoundView(rs.getInt("round"), rs.getString("name"),
                        Rows.enumOf(rs, "kind", RoundKind.class), Rows.enumOf(rs, "mode", InterviewMode.class),
                        Rows.integer(rs, "duration_min"), Rows.integer(rs, "target_days")))
                .list();
    }

    /** The plan new jobs start from. Each job can still change its own. */
    @Transactional
    public List<RoundView> saveRoundPlan(List<RoundInput> input) {
        if (input == null || input.isEmpty()) {
            throw ApiException.badRequest("no_rounds", "The default plan needs at least one round.");
        }
        if (input.size() > ApplicationStage.MAX_ROUNDS) {
            throw ApiException.badRequest("too_many_rounds", "Up to " + ApplicationStage.MAX_ROUNDS + " rounds.");
        }
        UUID company = CurrentUser.companyId();
        jdbc.sql("delete from round_templates where company_id = :c").param("c", company).update();
        for (int i = 0; i < input.size(); i++) {
            RoundInput r = input.get(i);
            InterviewMode mode = r.kind() == RoundKind.PARTNER ? InterviewMode.IN_PERSON
                    : r.kind() == RoundKind.AI_INTERVIEW ? InterviewMode.ONLINE
                    : r.mode() == null ? InterviewMode.ONLINE : r.mode();
            jdbc.sql("""
                    insert into round_templates (company_id, round, name, kind, mode, duration_min, target_days, created_by)
                    values (:c, :r, :n, cast(:k as round_kind_t), cast(:m as interview_mode_t), :d, :t, :by)
                    """)
                    .param("c", company).param("r", i + 1).param("n", r.name().strip())
                    .param("k", r.kind().name().toLowerCase(Locale.ROOT)).param("m", mode.name().toLowerCase(Locale.ROOT))
                    .param("d", r.durationMin()).param("t", r.targetDays()).param("by", CurrentUser.id()).update();
        }
        activity.record("settings.round_plan_changed", "company", company);
        return roundPlan();
    }

    // ------------------------------------------------------------------ review measures

    @Transactional(readOnly = true)
    public List<String> measures() {
        return jdbc.sql("select name from review_measures where company_id = :c order by position, name")
                .param("c", CurrentUser.companyId()).query(String.class).list();
    }

    /** The general measures on the panel review form, in order. Reviews already sent keep their own. */
    @Transactional
    public List<String> saveMeasures(List<String> input) {
        List<String> names = input == null ? List.of() : input.stream()
                .map(n -> n == null ? "" : n.strip().replaceAll("\\s+", " ")).filter(n -> !n.isEmpty()).toList();
        if (names.isEmpty()) {
            throw ApiException.badRequest("no_measures", "Keep at least one measure, such as Communication.");
        }
        if (names.size() > 15) {
            throw ApiException.badRequest("too_many", "Up to 15 measures, so the form stays quick to fill in.");
        }
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (n.length() > 60) {
                throw ApiException.badRequest("too_long", "Keep each measure under 60 characters.");
            }
            if (!seen.add(n.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("duplicate", n + " is in the list twice.");
            }
        }
        UUID company = CurrentUser.companyId();
        jdbc.sql("delete from review_measures where company_id = :c").param("c", company).update();
        for (int i = 0; i < names.size(); i++) {
            jdbc.sql("insert into review_measures (company_id, name, position, created_by) values (:c, :n, :p, :by)")
                    .param("c", company).param("n", names.get(i)).param("p", i + 1).param("by", CurrentUser.id()).update();
        }
        activity.record("settings.review_measures_changed", "company", company);
        return measures();
    }

    // ------------------------------------------------------------------ ready-made "Not progressing" reasons

    @Transactional(readOnly = true)
    public List<String> decisionReasons() {
        return jdbc.sql("select reason from decision_reasons where company_id = :c order by position")
                .param("c", CurrentUser.companyId()).query(String.class).list();
    }

    /** Reasons HR can pick when not progressing a candidate. The candidate sees the reason, so keep them kind. */
    @Transactional
    public List<String> saveDecisionReasons(List<String> input) {
        List<String> reasons = input == null ? List.of() : input.stream()
                .map(r -> r == null ? "" : r.strip().replaceAll("\\s+", " ")).filter(r -> !r.isEmpty()).toList();
        if (reasons.size() > 20) {
            throw ApiException.badRequest("too_many", "Up to 20 reasons, so the list stays quick to pick from.");
        }
        Set<String> seen = new HashSet<>();
        for (String r : reasons) {
            if (r.length() < 10 || r.length() > 300) {
                throw ApiException.badRequest("reason_length", "Each reason needs 10 to 300 characters: \"" + r + "\"");
            }
            if (!seen.add(r.toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("duplicate", "This reason is in the list twice: \"" + r + "\"");
            }
        }
        UUID company = CurrentUser.companyId();
        jdbc.sql("delete from decision_reasons where company_id = :c").param("c", company).update();
        for (int i = 0; i < reasons.size(); i++) {
            jdbc.sql("insert into decision_reasons (company_id, reason, position, created_by) values (:c, :r, :p, :by)")
                    .param("c", company).param("r", reasons.get(i)).param("p", i + 1).param("by", CurrentUser.id()).update();
        }
        activity.record("settings.decision_reasons_changed", "company", company);
        return decisionReasons();
    }

    // ------------------------------------------------------------------ email templates

    @Transactional(readOnly = true)
    public List<EmailTemplateView> emailTemplates() {
        Map<String, String[]> custom = new LinkedHashMap<>();
        jdbc.sql("select template_key, subject, body from email_templates where company_id = :c")
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> custom.put(rs.getString("template_key"), new String[]{rs.getString("subject"), rs.getString("body")}))
                .list();
        return MessageTemplates.DEFINITIONS.values().stream().map(d -> {
            String[] c = custom.get(d.key());
            return new EmailTemplateView(d.key(), d.label(), d.description(), d.placeholders(),
                    c == null ? d.subject() : c[0], c == null ? d.body() : c[1], c != null, d.subject(), d.body());
        }).toList();
    }

    @Transactional
    public EmailTemplateView saveEmailTemplate(String key, EmailTemplateInput input) {
        if (!MessageTemplates.DEFINITIONS.containsKey(key)) {
            throw ApiException.notFound("Email template");
        }
        List<String> unknown = new java.util.ArrayList<>(MessageTemplates.unknownPlaceholders(key, input.subject()));
        MessageTemplates.unknownPlaceholders(key, input.body()).stream().filter(u -> !unknown.contains(u)).forEach(unknown::add);
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("unknown_placeholder", "This email can't use {{" + String.join("}}, {{", unknown)
                    + "}}. Use only the placeholders listed.");
        }
        jdbc.sql("""
                insert into email_templates (company_id, template_key, subject, body, created_by)
                values (:c, :k, :s, :b, :by)
                on conflict (company_id, template_key) do update set subject = excluded.subject, body = excluded.body
                """)
                .param("c", CurrentUser.companyId()).param("k", key).param("s", input.subject().strip())
                .param("b", input.body().strip()).param("by", CurrentUser.id()).update();
        activity.record("settings.email_template_changed", "company", CurrentUser.companyId());
        return emailTemplates().stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
    }

    /** Back to the portal's own wording. */
    @Transactional
    public EmailTemplateView resetEmailTemplate(String key) {
        if (!MessageTemplates.DEFINITIONS.containsKey(key)) {
            throw ApiException.notFound("Email template");
        }
        jdbc.sql("delete from email_templates where company_id = :c and template_key = :k")
                .param("c", CurrentUser.companyId()).param("k", key).update();
        return emailTemplates().stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
    }

    /** What the email looks like with sample details filled in. */
    public Preview preview(String key, EmailTemplateInput input) {
        if (!MessageTemplates.DEFINITIONS.containsKey(key)) {
            throw ApiException.notFound("Email template");
        }
        Map<String, String> sample = new LinkedHashMap<>();
        sample.put("firstName", "Priya");
        sample.put("jobTitle", "AWS Cloud Engineer");
        sample.put("company", company().name());
        sample.put("round", "Technical Level 1 (Round 2)");
        sample.put("nextRound", "Technical Level 2 (Round 3)");
        sample.put("when", "Thursday 12 November, 10:00 IST (04:30 GMT)");
        sample.put("length", "1 hour");
        sample.put("replyBy", "Tuesday 10 November, 18:00 IST");
        sample.put("format", "Online video call with the interview panel");
        sample.put("candidatePage", "Please accept this time by Tuesday 10 November, 18:00 IST. You can accept, ask for "
                + "another time or send us a message on your page:\nhttps://portal.example/my-interview/…");
        sample.put("arriveBy", "09:45 IST");
        sample.put("centre", "SkillBridge Madurai – Anna Nagar");
        sample.put("address", "Plot 14, 80 Feet Road, Anna Nagar, Madurai");
        sample.put("where", "Join here: https://meet.example.com/abc-defg-hij");
        sample.put("note", "We've moved it to the afternoon as you asked.");
        sample.put("reason", "The role needs more hands-on Kubernetes experience.");
        sample.put("candidate", "Priya Sharma");
        sample.put("code", "K7P4-QM2X");
        sample.put("signIn", "https://portal.example/panel");
        return new Preview(MessageTemplates.fill(input.subject(), sample).strip(), MessageTemplates.fill(input.body(), sample).strip());
    }
}
