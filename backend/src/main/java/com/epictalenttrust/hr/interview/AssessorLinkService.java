package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.mail.MailGateway;
import com.epictalenttrust.hr.notification.NotificationKind;
import com.epictalenttrust.hr.notification.NotificationService;
import com.epictalenttrust.hr.report.Recommendation;
import com.epictalenttrust.hr.security.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Partner assessors send their Round 2 report through a one-time link emailed to them: no account needed.
 * Only a hash of the link's token is stored; the link expires 14 days after the interview and works once.
 */
@Service
public class AssessorLinkService {

    public static final List<String> AREAS = List.of("Technical depth", "Problem solving", "Communication",
            "Practical task", "Professionalism");

    public record LinkView(String candidateName, String jobTitle, String companyName, Instant startAt, String centreName,
                           String assessorName, List<String> areas, boolean submitted, boolean expired) {
    }

    public record AssessorReport(Boolean idChecked, String idCheckNote, Map<String, Integer> areaScores,
                                 Recommendation recommendation, String notes) {
    }

    private static final Logger log = LoggerFactory.getLogger(AssessorLinkService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM, HH:mm z", Locale.ENGLISH);
    private static final Duration VALID_AFTER_INTERVIEW = Duration.ofDays(14);

    private final JdbcClient jdbc;
    private final MailGateway mail;
    private final NotificationService notifications;
    private final EttProperties properties;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public AssessorLinkService(JdbcClient jdbc, MailGateway mail, NotificationService notifications,
                               EttProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates the link and emails it to the assessor once the scheduling change is saved. */
    void issue(Interview interview, Job job, Candidate candidate) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        record Assessor(String name, String email) {
        }
        Assessor assessor = jdbc.sql("select full_name, email from partner_assessors where id = :id")
                .param("id", interview.getAssessorId())
                .query((rs, row) -> new Assessor(rs.getString("full_name"), rs.getString("email"))).single();
        jdbc.sql("""
                insert into assessor_links (interview_id, token_hash, sent_to, expires_at)
                values (:i, :h, :to, :exp)
                """)
                .param("i", interview.getId()).param("h", hash(token)).param("to", assessor.email())
                .param("exp", Timestamp.from(interview.getStartAt().plus(VALID_AFTER_INTERVIEW))).update();
        if (assessor.email() == null) {
            return;
        }
        String link = properties.portalUrl().replaceAll("/+$", "") + "/assessor/" + token;
        ZoneId zone = ZoneId.of(interview.getCandidateTz() == null ? "Asia/Kolkata" : interview.getCandidateTz());
        String text = """
                Hello %s,

                You're assessing %s for the %s role with %s.

                When: %s

                Please check the candidate's original Aadhaar card and PAN card before you start. After the interview, send your report here (no login needed, the link works once):

                %s

                Thank you,
                The ETT team""".formatted(firstName(assessor.name()), candidate.getFullName(), job.getTitle(),
                companyName(job.getCompanyId()), WHEN.format(interview.getStartAt().atZone(zone)), link);
        String subject = "Partner assessment: " + candidate.getFullName() + ", " + job.getTitle();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(assessor.email(), subject, text);
                }
            });
        } else {
            send(assessor.email(), subject, text);
        }
    }

    private void send(String to, String subject, String text) {
        String event = "sent";
        String detail = null;
        try {
            mail.send(to, subject, text, null);
        } catch (Exception ex) {
            log.warn("Assessor email to {} failed: {}", to, ex.getMessage());
            event = "failed";
            detail = ex.getMessage();
        }
        jdbc.sql("insert into email_log (to_address, provider, event, detail, occurred_at) values (:to, :p, cast(:e as email_event_t), :d, :at)")
                .param("to", to).param("p", mail.providerName()).param("e", event).param("d", detail)
                .param("at", Timestamp.from(clock.instant())).update();
    }

    @Transactional(readOnly = true)
    public LinkView view(String token) {
        Link link = link(token);
        return new LinkView(link.candidateName(), link.jobTitle(), link.companyName(), link.startAt(), link.centreName(),
                link.assessorName(), AREAS, link.usedAt() != null, link.expiresAt().isBefore(clock.instant()));
    }

    @Transactional
    public void submit(String token, AssessorReport report) {
        Link link = link(token);
        if (link.usedAt() != null) {
            throw ApiException.conflict("already_submitted", "This report has already been sent. Thank you.");
        }
        if (link.expiresAt().isBefore(clock.instant())) {
            throw ApiException.conflict("link_expired", "This link has expired. Please contact ETT.");
        }
        if (link.startAt() != null && link.startAt().isAfter(clock.instant())) {
            throw ApiException.conflict("too_early", "You can send the report once the interview has started.");
        }
        if (report.idChecked() == null || report.recommendation() == null) {
            throw ApiException.badRequest("incomplete", "Say whether you checked the ID documents, and give your recommendation.");
        }
        if (!report.idChecked() && (report.idCheckNote() == null || report.idCheckNote().isBlank())) {
            throw ApiException.badRequest("id_note_required", "Explain what happened with the ID check.");
        }
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String area : AREAS) {
            Integer value = report.areaScores() == null ? null : report.areaScores().get(area);
            if (value == null || value < 1 || value > 5) {
                throw ApiException.badRequest("scores_required", "Score every area from 1 to 5.");
            }
            scores.put(area, value);
        }
        int overall = (int) Math.round(scores.values().stream().mapToInt(Integer::intValue).average().orElse(0) / 5.0 * 100);
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> {
            saveAssessment(link.interviewId(), link.assessorId(), overall, report.recommendation(), report.idChecked(),
                    blank(report.idCheckNote()), toJson(scores), blank(report.notes()), false);
            jdbc.sql("update assessor_links set used_at = :at where id = :id")
                    .param("at", Timestamp.from(clock.instant())).param("id", link.id()).update();
            jdbc.sql("update interviews set status = 'completed', completed_at = coalesce(completed_at, :at) where id = :id")
                    .param("at", Timestamp.from(clock.instant())).param("id", link.interviewId()).update();
            jdbc.sql("insert into audit_log (actor_kind, action, record_type, record_id) values ('system', 'report.submitted', 'interview', :id)")
                    .param("id", link.interviewId()).update();
            notifications.notify(new NotificationService.Request(link.companyId(),
                    java.util.Arrays.asList(link.scheduledBy(), notifications.jobOwner(link.jobId())),
                    NotificationKind.REPORT_READY, "Round 2 report ready: " + link.candidateName(),
                    "The partner assessment centre sent its report for " + link.jobTitle() + ". Review it and decide the next step.",
                    "/applications/" + link.applicationId(), link.jobId(), link.applicationId(), link.interviewId()));
        });
    }

    /** Saves (or replaces) the Round 2 assessment for an interview. Also used for sample reports. */
    public void saveAssessment(UUID interviewId, UUID assessorId, int score, Recommendation recommendation, boolean idChecked,
                               String idNote, String areaScoresJson, String notes, boolean sample) {
        jdbc.sql("""
                insert into round2_assessments (interview_id, assessor_id, score, recommendation, id_checked, id_check_note,
                                                area_scores, notes, submitted_at, is_sample)
                values (:i, :a, :s, cast(:r as recommendation_t), :ok, :note, cast(:areas as jsonb), :notes, :at, :sample)
                on conflict (interview_id) do update set assessor_id = excluded.assessor_id, score = excluded.score,
                    recommendation = excluded.recommendation, id_checked = excluded.id_checked,
                    id_check_note = excluded.id_check_note, area_scores = excluded.area_scores, notes = excluded.notes,
                    submitted_at = excluded.submitted_at, is_sample = excluded.is_sample
                """)
                .param("i", interviewId).param("a", assessorId).param("s", score)
                .param("r", recommendation.name().toLowerCase(Locale.ROOT)).param("ok", idChecked).param("note", idNote)
                .param("areas", areaScoresJson).param("notes", notes).param("at", Timestamp.from(clock.instant()))
                .param("sample", sample).update();
    }

    private record Link(UUID id, UUID interviewId, UUID applicationId, UUID jobId, UUID companyId, UUID assessorId,
                        UUID scheduledBy, String candidateName, String jobTitle, String companyName, Instant startAt,
                        String centreName, String assessorName, Instant expiresAt, Instant usedAt) {
    }

    private Link link(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) {
            throw ApiException.notFound("Link");
        }
        return jdbc.sql("""
                        select l.id, l.interview_id, a.id as application_id, j.id as job_id, j.company_id, i.assessor_id,
                               i.scheduled_by, c.full_name, j.title, co.name as company_name, i.start_at,
                               pc.name as centre_name, pa.full_name as assessor_name, l.expires_at, l.used_at
                          from assessor_links l
                          join interviews i on i.id = l.interview_id
                          join applications a on a.id = i.application_id
                          join jobs j on j.id = a.job_id
                          join companies co on co.id = j.company_id
                          join candidates c on c.id = a.candidate_id
                          left join centre_slots cs on cs.id = i.slot_id
                          left join partner_centres pc on pc.id = cs.centre_id
                          left join partner_assessors pa on pa.id = i.assessor_id
                         where l.token_hash = :h and i.status <> 'cancelled'
                        """)
                .param("h", hash(token))
                .query((rs, row) -> new Link(Rows.uuid(rs, "id"), Rows.uuid(rs, "interview_id"), Rows.uuid(rs, "application_id"),
                        Rows.uuid(rs, "job_id"), Rows.uuid(rs, "company_id"), Rows.uuid(rs, "assessor_id"),
                        Rows.uuid(rs, "scheduled_by"), rs.getString("full_name"), rs.getString("title"),
                        rs.getString("company_name"), Rows.instant(rs, "start_at"), rs.getString("centre_name"),
                        rs.getString("assessor_name"), Rows.instant(rs, "expires_at"), Rows.instant(rs, "used_at")))
                .optional().orElseThrow(() -> ApiException.notFound("Link"));
    }

    private String companyName(UUID companyId) {
        return jdbc.sql("select name from companies where id = :id").param("id", companyId).query(String.class).single();
    }

    static String toJson(Map<String, Integer> scores) {
        StringBuilder json = new StringBuilder("{");
        scores.forEach((k, v) -> json.append(json.length() > 1 ? "," : "").append('"').append(k.replace("\"", "")).append("\":").append(v));
        return json.append('}').toString();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String firstName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName.strip().split("\\s+")[0];
    }
}
