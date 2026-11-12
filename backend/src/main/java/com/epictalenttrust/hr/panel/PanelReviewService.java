package com.epictalenttrust.hr.panel;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.interview.RoundSchedulingService;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.RoundKind;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.panel.PanelDtos.Average;
import com.epictalenttrust.hr.panel.PanelDtos.MemberReview;
import com.epictalenttrust.hr.panel.PanelDtos.PanelReport;
import com.epictalenttrust.hr.panel.PanelDtos.ReviewInput;
import com.epictalenttrust.hr.panel.PanelDtos.ReviewPage;
import com.epictalenttrust.hr.panel.PanelDtos.Score;
import com.epictalenttrust.hr.panel.PanelDtos.Skill;
import com.epictalenttrust.hr.report.Recommendation;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.security.HrUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Panel reviews. The generic panel login opens one review at a time: the panel member enters their employee ID and
 * the interview code, and only if that ID is on that interview's panel does the review open. Each member writes one
 * detailed review (scores for the job's skills and the company's general measures, written feedback and a
 * recommendation); together they make the round's report for HR.
 */
@Service
public class PanelReviewService {

    static final int MAX_WRONG_TRIES = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final List<String> RECOMMENDATIONS = List.of("strong_hire", "hire", "no_hire", "strong_no_hire");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final NotificationService notifications;
    private final Clock clock;
    private final PanelWindow window;

    public PanelReviewService(JdbcClient jdbc, ObjectMapper json, NotificationService notifications, Clock clock,
                              PanelWindow window) {
        this.window = window;
        this.jdbc = jdbc;
        this.json = json;
        this.notifications = notifications;
        this.clock = clock;
    }

    private record Member(UUID id, UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, String fullName,
                          String employeeId, String status, Instant startAt, Integer durationMin, int round,
                          boolean decided, PanelWindow window) {

        Instant openFrom() {
            return window.opensAt(startAt);
        }

        Instant closesAt() {
            return window.closesAt(startAt, durationMin);
        }
    }

    // ------------------------------------------------------------------ signing in to one review

    /**
     * Checks the employee ID and interview code. Returns the panel member's id (kept in the session) or explains
     * why the review can't open. Five wrong tries lock the panel login for 15 minutes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public UUID open(String employeeId, String code, String ip) {
        HrUserPrincipal me = CurrentUser.get();
        Instant now = clock.instant();
        Instant lockedUntil = lockedUntil(me.id(), now);
        if (lockedUntil != null) {
            long minutes = Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "panel_locked",
                    "Too many wrong tries. The panel login is locked for " + minutes + " more minute"
                            + (minutes == 1 ? "" : "s") + ". Check the code in your email and try again then.");
        }
        String id = employeeId == null ? "" : employeeId.strip();
        Member member = jdbc.sql(MEMBER_SQL + """
                         where i.panel_code_hash = :hash and j.company_id = :company and upper(m.employee_id) = upper(:emp)
                        """)
                .param("hash", RoundSchedulingService.hashCode(code)).param("company", me.companyId()).param("emp", id)
                .query(this::mapMember).optional().orElse(null);
        if (member == null) {
            attempt(me.id(), id, false, "no_match", ip);
            int left = MAX_WRONG_TRIES - failuresSince(me.id(), now.minus(LOCKOUT));
            throw ApiException.badRequest("no_match", "That employee ID and interview code don't match an interview."
                    + (left > 0 && left < MAX_WRONG_TRIES ? " " + left + (left == 1 ? " try" : " tries") + " left before a 15-minute lock." : ""));
        }
        attempt(me.id(), id, true, null, ip);
        String closed = closedReason(member, now);
        if ("cancelled".equals(member.status())) {
            throw ApiException.conflict("interview_cancelled", "This interview was cancelled, so there's nothing to review.");
        }
        if (now.isBefore(member.openFrom())) {
            throw ApiException.conflict("too_early", "This code opens at " + WHEN.format(member.openFrom().atZone(zone(member)))
                    + ", an hour before the interview.");
        }
        if (closed != null && !hasReview(member.id())) {
            throw ApiException.conflict("closed", closed);
        }
        return member.id();
    }

    /** The review page for the panel member in this session. */
    @Transactional(readOnly = true)
    public ReviewPage page(UUID memberId) {
        Member member = member(memberId);
        record Ctx(String candidate, String job, String company, String roundName, RoundKind kind, InterviewMode mode,
                   String link, String venue, boolean cv, String description) {
        }
        Ctx ctx = jdbc.sql("""
                        select c.full_name, j.title, co.name as company, jr.name as round_name, jr.kind, i.mode,
                               i.meeting_link, i.venue, a.cv_file_key is not null as cv, j.description
                          from interviews i join applications a on a.id = i.application_id
                          join jobs j on j.id = a.job_id join companies co on co.id = j.company_id
                          join candidates c on c.id = a.candidate_id
                          left join job_rounds jr on jr.job_id = j.id and jr.round = i.round
                         where i.id = :i
                        """)
                .param("i", member.interviewId())
                .query((rs, n) -> new Ctx(rs.getString("full_name"), rs.getString("title"), rs.getString("company"),
                        rs.getString("round_name"), Rows.enumOf(rs, "kind", RoundKind.class),
                        Rows.enumOf(rs, "mode", InterviewMode.class), rs.getString("meeting_link"), rs.getString("venue"),
                        rs.getBoolean("cv"), rs.getString("description")))
                .single();
        Saved saved = saved(memberId);
        Instant now = clock.instant();
        String closed = closedReason(member, now);
        return new ReviewPage(member.interviewId(), ctx.candidate(), ctx.job(), ctx.company(), member.round(),
                ctx.roundName(), ctx.kind(), ctx.mode(), member.startAt(), member.durationMin(), ctx.link(), ctx.venue(),
                ctx.cv(), ctx.description(), skills(member.jobId()), measures(member.companyId()), member.fullName(),
                member.employeeId(), saved == null ? null : saved.input(), saved == null ? null : saved.submittedAt(),
                saved == null ? null : saved.overall(), closed == null && !now.isBefore(member.startAt()),
                member.openFrom(), member.closesAt(), closed);
    }

    /** Saves a draft (as the reviewer types) or submits the review. */
    @Transactional
    public ReviewPage save(UUID memberId, ReviewInput input, boolean submit, String ip, String device) {
        Member member = member(memberId);
        Instant now = clock.instant();
        String closed = closedReason(member, now);
        if (closed != null) {
            throw ApiException.conflict("closed", closed);
        }
        if (now.isBefore(member.startAt())) {
            throw ApiException.conflict("too_early", "The review form opens when the interview starts, at "
                    + WHEN.format(member.startAt().atZone(zone(member))) + ".");
        }
        actAs(member);
        List<Skill> skills = skills(member.jobId());
        List<String> measures = measures(member.companyId());
        List<Score> skillScores = keepKnown(input.skills(), skills.stream().map(Skill::name).toList());
        List<Score> measureScores = keepKnown(input.measures(), measures);
        String recommendation = input.recommendation() == null || input.recommendation().isBlank() ? null
                : input.recommendation().strip().toLowerCase(Locale.ROOT);
        if (recommendation != null && !RECOMMENDATIONS.contains(recommendation)) {
            throw ApiException.badRequest("invalid_recommendation", "Choose Strong hire, Hire, No hire or Strong no hire.");
        }
        Double overall = overall(skillScores, measureScores);
        if (submit) {
            List<String> missing = new ArrayList<>();
            skills.stream().filter(s -> scoreOf(skillScores, s.name()) == null).forEach(s -> missing.add(s.name()));
            measures.stream().filter(m -> scoreOf(measureScores, m) == null).forEach(missing::add);
            if (!missing.isEmpty()) {
                throw ApiException.badRequest("scores_missing", "Score every skill and measure before sending. Still to score: "
                        + String.join(", ", missing.subList(0, Math.min(5, missing.size())))
                        + (missing.size() > 5 ? " and " + (missing.size() - 5) + " more" : "") + ".");
            }
            requireText(input.strengths(), "strengths", 10);
            requireText(input.concerns(), "concerns or areas to improve", 10);
            requireText(input.summary(), "overall summary", 30);
            if (recommendation == null) {
                throw ApiException.badRequest("recommendation_required", "Choose your recommendation before sending.");
            }
        }
        boolean wasSubmitted = saved(memberId) != null && saved(memberId).submittedAt() != null;
        jdbc.sql("""
                insert into panel_reviews (interview_id, panel_member_id, skill_scores, measure_scores, strengths, concerns,
                                           questions, summary, next_round_notes, recommendation, overall_score,
                                           submitted_at, device, ip_address, created_by)
                values (:i, :m, cast(:skills as jsonb), cast(:measures as jsonb), :strengths, :concerns, :questions,
                        :summary, :next, :rec, :overall, :submitted, :device, :ip, :by)
                on conflict (panel_member_id) do update set
                       skill_scores = excluded.skill_scores, measure_scores = excluded.measure_scores,
                       strengths = excluded.strengths, concerns = excluded.concerns, questions = excluded.questions,
                       summary = excluded.summary, next_round_notes = excluded.next_round_notes,
                       recommendation = excluded.recommendation, overall_score = excluded.overall_score,
                       submitted_at = coalesce(excluded.submitted_at, panel_reviews.submitted_at),
                       device = excluded.device, ip_address = excluded.ip_address
                """)
                .param("i", member.interviewId()).param("m", memberId)
                .param("skills", json.writeValueAsString(skillScores)).param("measures", json.writeValueAsString(measureScores))
                .param("strengths", trim(input.strengths())).param("concerns", trim(input.concerns()))
                .param("questions", trim(input.questions())).param("summary", trim(input.summary()))
                .param("next", trim(input.nextRoundNotes())).param("rec", recommendation)
                .param("overall", overall).param("submitted", submit ? Timestamp.from(now) : null)
                .param("device", device == null ? null : device.substring(0, Math.min(300, device.length())))
                .param("ip", ip).param("by", CurrentUser.idOrNull())
                .update();
        if (submit) {
            tellHr(member, wasSubmitted);
        }
        return page(memberId);
    }

    /** Fills in a sample review for every member who hasn't sent one (sample reports, for trying the flow). */
    @Transactional
    public void saveSamples(UUID interviewId, java.util.Random random) {
        List<UUID> members = jdbc.sql("""
                        select m.id from panel_members m left join panel_reviews r on r.panel_member_id = m.id
                         where m.interview_id = :i and (r.id is null or r.submitted_at is null) order by m.created_at
                        """)
                .param("i", interviewId).query(UUID.class).list();
        if (members.isEmpty()) {
            return;
        }
        Member first = jdbc.sql(MEMBER_SQL + " where m.id = :id").param("id", members.getFirst())
                .query(this::mapMember).single();
        List<Skill> skills = skills(first.jobId());
        List<String> measures = measures(first.companyId());
        int base = 2 + random.nextInt(3);
        for (UUID memberId : members) {
            List<Score> skillScores = skills.stream()
                    .map(s -> new Score(s.name(), clamp(base + random.nextInt(3) - 1), sampleComment(s.name(), random))).toList();
            List<Score> measureScores = measures.stream()
                    .map(m -> new Score(m, clamp(base + random.nextInt(3) - 1), null)).toList();
            Double overall = overall(skillScores, measureScores);
            String rec = overall >= 4.2 ? "strong_hire" : overall >= 3.4 ? "hire" : overall >= 2.4 ? "no_hire" : "strong_no_hire";
            jdbc.sql("""
                    insert into panel_reviews (interview_id, panel_member_id, skill_scores, measure_scores, strengths,
                                               concerns, questions, summary, next_round_notes, recommendation,
                                               overall_score, submitted_at, is_sample)
                    values (:i, :m, cast(:skills as jsonb), cast(:measures as jsonb), :s, :c, :q, :sum, :n, :rec, :o, now(), true)
                    on conflict (panel_member_id) do update set skill_scores = excluded.skill_scores,
                           measure_scores = excluded.measure_scores, strengths = excluded.strengths,
                           concerns = excluded.concerns, questions = excluded.questions, summary = excluded.summary,
                           next_round_notes = excluded.next_round_notes, recommendation = excluded.recommendation,
                           overall_score = excluded.overall_score, submitted_at = now(), is_sample = true
                    """)
                    .param("i", interviewId).param("m", memberId)
                    .param("skills", json.writeValueAsString(skillScores))
                    .param("measures", json.writeValueAsString(measureScores))
                    .param("s", "Sample review. Clear, structured answers; worked through the practical question step by step.")
                    .param("c", "Sample review. Could go deeper on trade-offs and on how they would test their own work.")
                    .param("q", "Sample review. Walk us through a recent production issue; how would you design a rollback?")
                    .param("sum", "Sample review. A solid interview overall: good grasp of the core skills for this role, "
                            + "with a couple of areas to explore further in the next round.")
                    .param("n", "Sample review. Probe system design and stakeholder communication.")
                    .param("rec", rec).param("o", overall)
                    .update();
        }
    }

    // ------------------------------------------------------------------ the round report for HR

    /** Every panel member's review for one interview, combined: averages, recommendations and comments. */
    @Transactional(readOnly = true)
    public PanelReport report(UUID interviewId) {
        record Info(int round, String roundName, Instant startAt, UUID jobId) {
        }
        Info info = jdbc.sql("""
                        select i.round, jr.name, i.start_at, a.job_id from interviews i
                          join applications a on a.id = i.application_id
                          left join job_rounds jr on jr.job_id = a.job_id and jr.round = i.round
                         where i.id = :i
                        """)
                .param("i", interviewId)
                .query((rs, n) -> new Info(rs.getInt("round"), rs.getString("name"), Rows.instant(rs, "start_at"),
                        Rows.uuid(rs, "job_id")))
                .single();
        List<MemberReview> reviews = jdbc.sql("""
                        select m.id, m.full_name, m.employee_id, m.email, r.submitted_at, r.overall_score, r.recommendation,
                               r.skill_scores::text as skills, r.measure_scores::text as measures, r.strengths, r.concerns,
                               r.questions, r.summary, r.next_round_notes, coalesce(r.is_sample, false) as sample
                          from panel_members m left join panel_reviews r on r.panel_member_id = m.id
                         where m.interview_id = :i order by r.submitted_at nulls last, m.full_name
                        """)
                .param("i", interviewId)
                .query((rs, n) -> new MemberReview(Rows.uuid(rs, "id"), rs.getString("full_name"),
                        rs.getString("employee_id"), rs.getString("email"), Rows.instant(rs, "submitted_at"),
                        (Double) (rs.getObject("overall_score") == null ? null : rs.getBigDecimal("overall_score").doubleValue()),
                        rs.getString("recommendation"),
                        rs.getTimestamp("submitted_at") == null ? List.of() : scores(rs.getString("skills")),
                        rs.getTimestamp("submitted_at") == null ? List.of() : scores(rs.getString("measures")),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getString("strengths"),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getString("concerns"),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getString("questions"),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getString("summary"),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getString("next_round_notes"),
                        rs.getBoolean("sample")))
                .list();
        List<MemberReview> sent = reviews.stream().filter(r -> r.submittedAt() != null).toList();
        Map<String, Integer> counts = new LinkedHashMap<>();
        RECOMMENDATIONS.forEach(r -> counts.put(r, 0));
        sent.forEach(r -> counts.merge(r.recommendation(), 1, Integer::sum));
        List<Skill> skills = skills(info.jobId());
        List<Average> skillAverages = skills.stream()
                .map(s -> average(s.name(), s.mustHave(), sent.stream().map(r -> scoreOf(r.skills(), s.name())).toList()))
                .toList();
        List<String> measureNames = new ArrayList<>();
        sent.forEach(r -> r.measures().forEach(m -> {
            if (!measureNames.contains(m.name())) {
                measureNames.add(m.name());
            }
        }));
        List<Average> measureAverages = measureNames.stream()
                .map(m -> average(m, false, sent.stream().map(r -> scoreOf(r.measures(), m)).toList())).toList();
        Double overall = sent.isEmpty() ? null
                : round1(sent.stream().filter(r -> r.overallScore() != null).mapToDouble(MemberReview::overallScore).average().orElse(0));
        return new PanelReport(interviewId, info.round(), info.roundName(), info.startAt(), reviews.size(), sent.size(),
                overall, counts, combined(counts), skillAverages, measureAverages, reviews,
                sent.stream().anyMatch(MemberReview::sample));
    }

    /** Most reviewers' view: hire or strong hire → move on; no hire or strong no hire → don't; a tie → hold. */
    static Recommendation combined(Map<String, Integer> counts) {
        int yes = counts.getOrDefault("strong_hire", 0) + counts.getOrDefault("hire", 0);
        int no = counts.getOrDefault("no_hire", 0) + counts.getOrDefault("strong_no_hire", 0);
        if (yes == 0 && no == 0) {
            return null;
        }
        return yes > no ? Recommendation.ADVANCE : no > yes ? Recommendation.REJECT : Recommendation.HOLD;
    }

    // ------------------------------------------------------------------ helpers

    private static final String MEMBER_SQL = """
            select m.id, m.interview_id, i.application_id, a.job_id, j.company_id, m.full_name, m.employee_id,
                   i.status, i.start_at, i.duration_min, i.round,
                   exists (select 1 from stage_decisions d where d.application_id = a.id
                             and d.stage::text = 'round' || i.round and d.decision <> 'hold') as decided
              from panel_members m join interviews i on i.id = m.interview_id
              join applications a on a.id = i.application_id join jobs j on j.id = a.job_id
            """;

    private Member member(UUID memberId) {
        HrUserPrincipal me = CurrentUser.get();
        return jdbc.sql(MEMBER_SQL + " where m.id = :id and j.company_id = :company")
                .param("id", memberId).param("company", me.companyId())
                .query(this::mapMember).optional()
                .orElseThrow(() -> ApiException.notFound("Review"));
    }

    private Member mapMember(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Member(Rows.uuid(rs, "id"), Rows.uuid(rs, "interview_id"), Rows.uuid(rs, "application_id"),
                Rows.uuid(rs, "job_id"), Rows.uuid(rs, "company_id"), rs.getString("full_name"),
                rs.getString("employee_id"), rs.getString("status"), Rows.instant(rs, "start_at"),
                Rows.integer(rs, "duration_min"), rs.getInt("round"), rs.getBoolean("decided"), window);
    }

    private String closedReason(Member member, Instant now) {
        if ("cancelled".equals(member.status())) {
            return "This interview was cancelled.";
        }
        if (member.decided()) {
            return "HR has made the decision for this round, so reviews are closed.";
        }
        if (now.isAfter(member.closesAt())) {
            return "Reviews for this interview closed on " + WHEN.format(member.closesAt().atZone(zone(member)))
                    + ". Ask HR if you still need to add yours.";
        }
        return null;
    }

    private record Saved(ReviewInput input, Instant submittedAt, Double overall) {
    }

    private Saved saved(UUID memberId) {
        return jdbc.sql("""
                        select skill_scores::text as skills, measure_scores::text as measures, strengths, concerns, questions,
                               summary, next_round_notes, recommendation, submitted_at, overall_score
                          from panel_reviews where panel_member_id = :m
                        """)
                .param("m", memberId)
                .query((rs, n) -> new Saved(new ReviewInput(scores(rs.getString("skills")), scores(rs.getString("measures")),
                        rs.getString("strengths"), rs.getString("concerns"), rs.getString("questions"),
                        rs.getString("summary"), rs.getString("next_round_notes"), rs.getString("recommendation")),
                        Rows.instant(rs, "submitted_at"),
                        rs.getObject("overall_score") == null ? null : rs.getBigDecimal("overall_score").doubleValue()))
                .optional().orElse(null);
    }

    private boolean hasReview(UUID memberId) {
        return jdbc.sql("select exists (select 1 from panel_reviews where panel_member_id = :m)").param("m", memberId)
                .query(Boolean.class).single();
    }

    /** The job's skills: must-haves first, then nice-to-haves. */
    List<Skill> skills(UUID jobId) {
        return jdbc.sql("""
                        select value, kind = 'must_have' as must from job_criteria
                         where job_id = :j and retired_at is null and kind in ('must_have', 'nice_to_have')
                         order by case kind when 'must_have' then 0 else 1 end, created_at
                        """)
                .param("j", jobId)
                .query((rs, n) -> new Skill(rs.getString("value"), rs.getBoolean("must"))).list();
    }

    List<String> measures(UUID companyId) {
        return jdbc.sql("select name from review_measures where company_id = :c order by position, name")
                .param("c", companyId).query(String.class).list();
    }

    private List<Score> scores(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return json.readValue(text, new TypeReference<List<Score>>() {
        });
    }

    private static List<Score> keepKnown(List<Score> input, List<String> allowed) {
        if (input == null) {
            return List.of();
        }
        Map<String, Score> byName = new LinkedHashMap<>();
        for (Score s : input) {
            allowed.stream().filter(a -> a.equalsIgnoreCase(s.name().strip())).findFirst().ifPresent(name ->
                    byName.put(name, new Score(name, s.score(), trim(s.comment()))));
        }
        return allowed.stream().filter(byName::containsKey).map(byName::get).toList();
    }

    private static Integer scoreOf(List<Score> scores, String name) {
        return scores.stream().filter(s -> s.name().equalsIgnoreCase(name)).map(Score::score).findFirst().orElse(null);
    }

    /** Overall score out of 5: the job's skills count for 60%, the general measures for 40%. */
    static Double overall(List<Score> skills, List<Score> measures) {
        Double s = mean(skills);
        Double m = mean(measures);
        if (s == null && m == null) {
            return null;
        }
        if (s == null) {
            return round1(m);
        }
        if (m == null) {
            return round1(s);
        }
        return round1(s * 0.6 + m * 0.4);
    }

    private static Double mean(List<Score> scores) {
        var values = scores.stream().map(Score::score).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue);
        var stats = values.summaryStatistics();
        return stats.getCount() == 0 ? null : stats.getAverage();
    }

    private static Average average(String name, boolean must, List<Integer> values) {
        List<Integer> scored = values.stream().filter(java.util.Objects::nonNull).toList();
        return new Average(name, must, scored.isEmpty() ? null
                : round1(scored.stream().mapToInt(Integer::intValue).average().orElse(0)), scored.size());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static int clamp(int score) {
        return Math.max(1, Math.min(5, score));
    }

    private static String sampleComment(String skill, java.util.Random random) {
        String[] notes = {"Answered the scenario questions on %s confidently.", "Good working knowledge of %s.",
                "Basic grasp of %s; would need support at first.", "Explained real project use of %s clearly."};
        return "Sample. " + notes[random.nextInt(notes.length)].formatted(skill);
    }

    private static void requireText(String value, String what, int min) {
        if (value == null || value.strip().length() < min) {
            throw ApiException.badRequest("text_required", "Add the " + what + " (at least " + min + " characters) before sending.");
        }
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Recorded in the audit trail as this panel member (name and employee ID), not the shared panel login. */
    private void actAs(Member member) {
        jdbc.sql("""
                select set_config('app.current_actor_kind', 'panel_member', true),
                       set_config('app.actor_label', :label, true)
                """)
                .param("label", "Panel: " + member.fullName() + " (" + member.employeeId() + ")")
                .query((rs, n) -> 0).list();
    }

    private void attempt(UUID userId, String employeeId, boolean ok, String reason, String ip) {
        jdbc.sql("""
                insert into panel_access_attempts (hr_user_id, employee_id, succeeded, reason, ip_address)
                values (:u, :e, :ok, :r, :ip)
                """)
                .param("u", userId).param("e", employeeId).param("ok", ok).param("r", reason).param("ip", ip).update();
    }

    private int failuresSince(UUID userId, Instant since) {
        return jdbc.sql("""
                        select count(*) from panel_access_attempts
                         where hr_user_id = :u and not succeeded and at > greatest(:since,
                               coalesce((select max(at) from panel_access_attempts where hr_user_id = :u and succeeded), :since))
                        """)
                .param("u", userId).param("since", Timestamp.from(since)).query(Integer.class).single();
    }

    /** When the lock ends, if five wrong tries were made within the last 15 minutes (since the last success). */
    private Instant lockedUntil(UUID userId, Instant now) {
        if (failuresSince(userId, now.minus(LOCKOUT)) < MAX_WRONG_TRIES) {
            return null;
        }
        Instant last = jdbc.sql("select max(at) from panel_access_attempts where hr_user_id = :u and not succeeded")
                .param("u", userId).query(Timestamp.class).single().toInstant();
        Instant until = last.plus(LOCKOUT);
        return until.isAfter(now) ? until : null;
    }

    private ZoneId zone(Member member) {
        String zone = jdbc.sql("select time_zone from companies where id = :c").param("c", member.companyId())
                .query(String.class).optional().orElse("Asia/Kolkata");
        return com.epictalenttrust.hr.common.TimeZones.parseOr(zone, com.epictalenttrust.hr.common.TimeZones.INDIA);
    }

    private void tellHr(Member member, boolean update) {
        record Info(String candidate, String title, UUID owner, UUID scheduledBy, int members, int submitted) {
        }
        Info info = jdbc.sql("""
                        select c.full_name, j.title, j.owner_id, i.scheduled_by,
                               (select count(*) from panel_members where interview_id = i.id) as members,
                               (select count(*) from panel_reviews where interview_id = i.id and submitted_at is not null) as sent
                          from interviews i join applications a on a.id = i.application_id
                          join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                         where i.id = :i
                        """)
                .param("i", member.interviewId())
                .query((rs, n) -> new Info(rs.getString("full_name"), rs.getString("title"), Rows.uuid(rs, "owner_id"),
                        Rows.uuid(rs, "scheduled_by"), rs.getInt("members"), rs.getInt("sent")))
                .single();
        boolean all = info.submitted() >= info.members();
        List<UUID> to = new ArrayList<>(Set.of(info.owner() == null ? notifications.jobOwner(member.jobId()) : info.owner()));
        if (info.scheduledBy() != null && !to.contains(info.scheduledBy())) {
            to.add(info.scheduledBy());
        }
        notifications.notify(new NotificationService.Request(member.companyId(), to,
                all ? NotificationKind.REPORT_READY : NotificationKind.REVIEW_SUBMITTED,
                all ? "Panel report ready: " + info.candidate()
                        : member.fullName() + (update ? " updated their review of " : " reviewed ") + info.candidate(),
                all ? "All " + info.members() + " panel reviews are in for " + info.title() + ". Review the report and decide."
                        : info.submitted() + " of " + info.members() + " panel reviews are in for " + info.title() + ".",
                "/applications/" + member.applicationId(), member.jobId(), member.applicationId(), member.interviewId()));
    }

    static Map<String, String> labels() {
        Map<String, String> m = new HashMap<>();
        m.put("strong_hire", "Strong hire");
        m.put("hire", "Hire");
        m.put("no_hire", "No hire");
        m.put("strong_no_hire", "Strong no hire");
        return m;
    }
}
