package com.epictalenttrust.hr.audit;

import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.ChangeLog;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.security.Access;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
 * The audit trail: every change to jobs, candidates and interviews, with who made it and exactly what changed. It reads
 * the change log the database keeps for every row (append-only: entries can't be edited or deleted). HR admins see
 * everything; recruiters and hiring managers see changes on their own jobs and any they're covering.
 */
@Service
public class AuditTrailService {

    public record Change(String field, String from, String to) {
    }

    public record Entry(long id, Instant at, String who, String actorKind, String action, String type, String recordName,
                        String detail, String link, List<Change> changes) {
    }

    public record Page(List<Entry> items, boolean more) {
    }

    public record Filter(String type, String person, LocalDate from, LocalDate to, UUID jobId, UUID applicationId,
                         UUID interviewId, String query, Long before, int size) {
    }

    private static final Map<String, String> TYPE_OF = Map.ofEntries(
            Map.entry("jobs", "job"), Map.entry("job_rounds", "job"), Map.entry("job_criteria", "job"),
            Map.entry("candidates", "candidate"), Map.entry("applications", "candidate"),
            Map.entry("stage_decisions", "candidate"), Map.entry("shortlist_decisions", "candidate"),
            Map.entry("application_notes", "candidate"), Map.entry("interviews", "interview"),
            Map.entry("panel_members", "interview"), Map.entry("panel_reviews", "interview"),
            Map.entry("reschedule_requests", "interview"), Map.entry("round2_assessments", "interview"),
            Map.entry("round1_reports", "interview"));

    /** Fields never shown: ids, bookkeeping and anything secret. */
    private static final Set<String> HIDDEN = Set.of("id", "created_at", "created_by", "last_modified_at",
            "last_modified_by", "assigned_id", "company_id", "job_id", "application_id", "candidate_id", "interview_id",
            "panel_member_id", "screening_id", "cv_text_key", "cv_file_key", "cv_text_hash", "panel_code_hash",
            "panel_code_enc", "token_hash", "device", "ip_address", "criteria_version", "description_file_key",
            "reminder_sent_at", "due_reminder_sent_at", "stage_entered_at", "slot_id", "is_sample", "time_source",
            "required", "report_key", "location_pin", "location_place", "location_district", "location_region",
            "location_country", "location_lat", "location_lng", "location_updated_at", "candidate_pin", "message_id",
            "entered_before", "send_attempts", "next_attempt_at");

    private static final Map<String, String> LABELS = labels();
    private static final Set<String> PEOPLE = Set.of("owner_id", "hiring_manager_id", "scheduled_by", "decided_by",
            "status_changed_by", "cv_uploaded_by", "author_id", "resolved_by", "reviewed_by");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final JdbcClient jdbc;
    private final Access access;
    private final ObjectMapper json;

    public AuditTrailService(JdbcClient jdbc, Access access, ObjectMapper json) {
        this.jdbc = jdbc;
        this.access = access;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Page list(Filter filter) {
        int size = Math.min(Math.max(filter.size(), 1), 200);
        List<String> tables = TYPE_OF.entrySet().stream()
                .filter(e -> filter.type() == null || filter.type().isBlank() || e.getValue().equals(filter.type()))
                .map(Map.Entry::getKey).toList();
        if (tables.isEmpty()) {
            throw ApiException.badRequest("invalid_type", "Type is job, candidate or interview.");
        }
        ZoneId zone = companyZone();
        String q = filter.query() == null || filter.query().isBlank() ? null : "%" + filter.query().strip().toLowerCase(Locale.ROOT) + "%";
        List<Row> rows = access.bind(jdbc.sql(SQL))
                .param("tables", tables)
                .param("from", filter.from() == null ? null : filter.from().atStartOfDay(zone).toOffsetDateTime(), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("to", filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime(), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .param("job", filter.jobId()).param("app", filter.applicationId()).param("interview", filter.interviewId())
                .param("q", q).param("person", filter.person() == null || filter.person().isBlank() ? null : filter.person())
                .param("before", filter.before()).param("limit", size + 1)
                .query((rs, n) -> new Row(rs.getLong("id"), Rows.instant(rs, "changed_at"), rs.getString("table_name"),
                        rs.getString("operation"), rs.getString("old_text"), rs.getString("new_text"), rs.getString("who"),
                        rs.getString("actor_kind"), rs.getString("job_title"), Rows.uuid(rs, "job_id"),
                        rs.getString("candidate_name"), Rows.uuid(rs, "app_id"), rs.getInt("round"),
                        rs.getString("round_name")))
                .list();
        Map<UUID, String> people = people();
        List<Entry> entries = new ArrayList<>();
        for (Row row : rows.subList(0, Math.min(rows.size(), size))) {
            entries.add(entry(row, people, zone));
        }
        return new Page(entries, rows.size() > size);
    }

    /** The same rows as CSV, for a spreadsheet. */
    @Transactional(readOnly = true)
    public String csv(Filter filter) {
        StringBuilder out = new StringBuilder("When,Who,Type,Record,Action,What changed\n");
        ZoneId zone = companyZone();
        Long before = filter.before();
        for (int page = 0; page < 50; page++) {
            Page p = list(new Filter(filter.type(), filter.person(), filter.from(), filter.to(), filter.jobId(),
                    filter.applicationId(), filter.interviewId(), filter.query(), before, 200));
            for (Entry e : p.items()) {
                String what = e.changes().stream().map(c -> c.field() + ": " + (c.from() == null ? "" : c.from() + " → ")
                        + (c.to() == null ? "(removed)" : c.to())).reduce((a, b) -> a + "; " + b).orElse(e.detail() == null ? "" : e.detail());
                out.append(csv(WHEN.format(e.at().atZone(zone)))).append(',').append(csv(e.who())).append(',')
                        .append(csv(e.type())).append(',').append(csv(e.recordName())).append(',')
                        .append(csv(e.action())).append(',').append(csv(what)).append('\n');
            }
            if (!p.more() || p.items().isEmpty()) {
                break;
            }
            before = p.items().getLast().id();
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ query

    private record Row(long id, Instant at, String table, String operation, String oldText, String newText, String who,
                       String actorKind, String jobTitle, UUID jobId, String candidateName, UUID applicationId,
                       int round, String roundName) {
    }

    /** One row per change, resolved to its job, candidate and interview so it can be filtered and shown by name. */
    private static final String SQL = """
            with r as (
              select t.id, t.changed_at, t.table_name, t.record_id, t.operation, t.old_data::text as old_text,
                     t.new_data::text as new_text, t.actor_kind::text as actor_kind, %s as who,
                     coalesce(t.new_data, t.old_data) as d
                from audit_trail t
                left join hr_users u on u.id = t.changed_by
                left join hr_users ob on ob.id = t.on_behalf_of
               where t.table_name in (:tables)
                 and (cast(:from as timestamptz) is null or t.changed_at >= :from)
                 and (cast(:to as timestamptz) is null or t.changed_at < :to)
                 and (cast(:before as bigint) is null or t.id < :before)
            ), resolved as (
              select r.*,
                     case r.table_name
                       when 'interviews' then r.record_id
                       when 'panel_members' then (r.d->>'interview_id')::uuid
                       when 'panel_reviews' then (r.d->>'interview_id')::uuid
                       when 'reschedule_requests' then (r.d->>'interview_id')::uuid
                       when 'round2_assessments' then (r.d->>'interview_id')::uuid
                       when 'round1_reports' then (r.d->>'interview_id')::uuid
                     end as interview_id,
                     case r.table_name
                       when 'applications' then r.record_id
                       when 'stage_decisions' then (r.d->>'application_id')::uuid
                       when 'shortlist_decisions' then (r.d->>'application_id')::uuid
                       when 'application_notes' then (r.d->>'application_id')::uuid
                     end as direct_app,
                     case r.table_name
                       when 'jobs' then r.record_id
                       when 'job_rounds' then (r.d->>'job_id')::uuid
                       when 'job_criteria' then (r.d->>'job_id')::uuid
                     end as direct_job,
                     case when r.table_name = 'candidates' then r.record_id end as direct_candidate
                from r
            ), linked as (
              select x.*, coalesce(x.direct_app, i.application_id) as app_id, i.round
                from resolved x left join interviews i on i.id = x.interview_id
            )
            select l.id, l.changed_at, l.table_name, l.operation, l.old_text, l.new_text, l.who, l.actor_kind,
                   j.id as job_id, j.title as job_title, l.app_id, c.full_name as candidate_name, l.round,
                   jr.name as round_name
              from linked l
              left join applications a on a.id = l.app_id
              left join jobs j on j.id = coalesce(l.direct_job, a.job_id)
              left join candidates c on c.id = coalesce(l.direct_candidate, a.candidate_id)
              left join job_rounds jr on jr.job_id = j.id and jr.round = l.round
             where (
                     (j.id is not null and j.company_id = :companyId
                      and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people)))
                  or (l.direct_candidate is not null and exists (
                        select 1 from applications a2 join jobs j2 on j2.id = a2.job_id
                         where a2.candidate_id = l.direct_candidate and j2.company_id = :companyId
                           and (:allJobs or j2.owner_id in (:people) or j2.hiring_manager_id in (:people))))
                   )
               and (cast(:job as uuid) is null or j.id = :job)
               and (cast(:app as uuid) is null or l.app_id = :app
                    or l.direct_candidate = (select candidate_id from applications where id = :app))
               and (cast(:interview as uuid) is null or l.interview_id = :interview)
               and (cast(:person as text) is null or l.who ilike '%%' || :person || '%%')
               and (cast(:q as text) is null or lower(coalesce(j.title, '')) like :q or lower(coalesce(c.full_name, '')) like :q
                    or lower(l.who) like :q)
             order by l.id desc
             limit :limit
            """.formatted(ChangeLog.WHO);

    // ------------------------------------------------------------------ describing a change

    private Entry entry(Row row, Map<UUID, String> people, ZoneId zone) {
        JsonNode before = row.oldText() == null ? null : json.readTree(row.oldText());
        JsonNode after = row.newText() == null ? null : json.readTree(row.newText());
        String type = TYPE_OF.get(row.table());
        String roundLabel = row.round() > 0
                ? (row.roundName() == null ? "Round " + row.round() : row.roundName() + " (Round " + row.round() + ")") : null;
        String record = switch (type) {
            case "job" -> row.jobTitle();
            case "interview" -> (row.candidateName() == null ? "" : row.candidateName())
                    + (roundLabel == null ? "" : " · " + roundLabel);
            default -> row.candidateName() + (row.jobTitle() == null ? "" : " · " + row.jobTitle());
        };
        String link = row.applicationId() != null ? "/applications/" + row.applicationId()
                : row.jobId() != null ? "/jobs/" + row.jobId() : null;
        List<Change> changes = new ArrayList<>();
        String detail = null;
        String action;
        if ("INSERT".equals(row.operation())) {
            action = created(row.table());
            detail = summary(row.table(), after, people, zone);
        } else if ("DELETE".equals(row.operation())) {
            action = removed(row.table());
            detail = summary(row.table(), before, people, zone);
        } else {
            action = changed(row.table());
            if (after != null) {
                for (var it = after.propertyNames().iterator(); it.hasNext(); ) {
                    String field = it.next();
                    if (HIDDEN.contains(field)) {
                        continue;
                    }
                    String from = value(field, before == null ? null : before.get(field), people, zone);
                    String to = value(field, after.get(field), people, zone);
                    if (!java.util.Objects.equals(from, to)) {
                        changes.add(new Change(label(row.table(), field), from, to));
                    }
                }
            }
            if (changes.isEmpty()) {
                return new Entry(row.id(), row.at(), row.who(), row.actorKind(), action, type, record, "No visible change",
                        link, changes);
            }
        }
        return new Entry(row.id(), row.at(), row.who(), row.actorKind(), action, type, record, detail, link, changes);
    }

    private static String created(String table) {
        return switch (table) {
            case "jobs" -> "Created the job";
            case "job_rounds" -> "Added a round";
            case "job_criteria" -> "Added a skill";
            case "candidates" -> "Added the candidate";
            case "applications" -> "Added to the job";
            case "stage_decisions" -> "Made a round decision";
            case "shortlist_decisions" -> "Made a shortlist decision";
            case "application_notes" -> "Added a note";
            case "interviews" -> "Booked an interview";
            case "panel_members" -> "Added a panel member";
            case "panel_reviews" -> "Started a panel review";
            case "reschedule_requests" -> "Asked for a new time";
            case "round2_assessments" -> "Sent the assessor's report";
            case "round1_reports" -> "Added the AI interview report";
            default -> "Created";
        };
    }

    private static String changed(String table) {
        return switch (table) {
            case "jobs" -> "Changed the job";
            case "job_rounds" -> "Changed a round";
            case "job_criteria" -> "Changed a skill";
            case "candidates" -> "Changed the candidate's details";
            case "applications" -> "Changed the candidate's progress";
            case "interviews" -> "Changed the interview";
            case "panel_members" -> "Changed a panel member";
            case "panel_reviews" -> "Updated a panel review";
            case "reschedule_requests" -> "Answered a request for a new time";
            default -> "Changed";
        };
    }

    private static String removed(String table) {
        return switch (table) {
            case "stage_decisions" -> "Undid a round decision";
            case "job_rounds" -> "Removed a round";
            case "job_criteria" -> "Removed a skill";
            case "panel_members" -> "Removed a panel member";
            default -> "Removed";
        };
    }

    /** A one-line description of a new or removed row. */
    private String summary(String table, JsonNode d, Map<UUID, String> people, ZoneId zone) {
        if (d == null) {
            return null;
        }
        return switch (table) {
            case "jobs" -> text(d, "title");
            case "job_rounds" -> text(d, "name") + " (" + value("kind", d.get("kind"), people, zone) + ")";
            case "job_criteria" -> text(d, "value") + " · " + value("kind", d.get("kind"), people, zone);
            case "stage_decisions" -> value("stage", d.get("stage"), people, zone) + ": "
                    + value("decision", d.get("decision"), people, zone)
                    + (d.hasNonNull("reason") ? " · " + text(d, "reason") : "");
            case "shortlist_decisions" -> value("decision", d.get("decision"), people, zone)
                    + (d.hasNonNull("reason") ? " · " + text(d, "reason") : "");
            case "interviews" -> value("start_at", d.get("start_at"), people, zone);
            case "panel_members" -> text(d, "full_name") + " (" + text(d, "employee_id") + ")";
            case "application_notes" -> shorten(text(d, "body"));
            case "candidates" -> text(d, "full_name");
            default -> null;
        };
    }

    private String value(String field, JsonNode node, Map<UUID, String> people, ZoneId zone) {
        if (node == null || node.isNull()) {
            return null;
        }
        String raw = node.isValueNode() ? node.asString() : node.toString();
        if (PEOPLE.contains(field)) {
            try {
                return people.getOrDefault(UUID.fromString(raw), "Someone");
            } catch (IllegalArgumentException ex) {
                return raw;
            }
        }
        if (field.endsWith("_at") || "reply_by".equals(field) || "arrive_by".equals(field)) {
            try {
                return WHEN.format(OffsetDateTime.parse(raw).atZoneSameInstant(zone));
            } catch (RuntimeException ex) {
                return raw;
            }
        }
        if (node.isBoolean()) {
            return node.asBoolean() ? "Yes" : "No";
        }
        if (node.isArray() || node.isObject()) {
            return shorten(raw);
        }
        return switch (field) {
            case "status", "stage", "kind", "mode", "decision", "recommendation", "contract_type", "reason",
                 "time_of_day", "source", "role" -> words(raw);
            default -> shorten(raw);
        };
    }

    private static String label(String table, String field) {
        String specific = LABELS.get(table + "." + field);
        if (specific != null) {
            return specific;
        }
        String general = LABELS.get(field);
        return general != null ? general : words(field.replace("_id", ""));
    }

    private static String words(String raw) {
        String s = raw.replace('_', ' ').strip().toLowerCase(Locale.ROOT);
        if (s.matches("round\\d")) {
            return "Round " + s.substring(5);
        }
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String text(JsonNode d, String field) {
        return d.hasNonNull(field) ? d.get(field).asString() : "";
    }

    private static String shorten(String s) {
        if (s == null) {
            return null;
        }
        String flat = s.replaceAll("\\s+", " ").strip();
        return flat.length() > 160 ? flat.substring(0, 157) + "…" : flat;
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")
                ? "'" + value : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private Map<UUID, String> people() {
        Map<UUID, String> out = new HashMap<>();
        jdbc.sql("select id, full_name from hr_users where company_id = :c").param("c", CurrentUser.companyId())
                .query((rs, n) -> out.put(Rows.uuid(rs, "id"), rs.getString("full_name"))).list();
        return out;
    }

    private ZoneId companyZone() {
        String zone = jdbc.sql("select time_zone from companies where id = :c").param("c", CurrentUser.companyId())
                .query(String.class).optional().orElse(null);
        return TimeZones.parseOr(zone, TimeZones.INDIA);
    }

    private static Map<String, String> labels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", "Title");
        m.put("cancel_reason", "Why it was cancelled");
        m.put("stage_before", "Stage before");
        m.put("notice_source", "Notice period from");
        m.put("ctc_source", "Expected CTC from");
        m.put("candidate_notice_days", "Notice period (said in the AI interview)");
        m.put("candidate_expected_ctc_lpa", "Expected CTC, lakh (said in the AI interview)");
        m.put("location", "Location");
        m.put("openings", "Openings");
        m.put("min_experience_yrs", "Minimum experience (years)");
        m.put("status", "Status");
        m.put("status_reason", "Reason");
        m.put("status_changed_at", "Status changed");
        m.put("status_changed_by", "Status changed by");
        m.put("published_at", "Published");
        m.put("owner_id", "Owner");
        m.put("hiring_manager_id", "Hiring manager");
        m.put("ai_shortlisting", "AI shortlisting");
        m.put("description", "Job description");
        m.put("description_file_name", "Job description file");
        m.put("accepted_locations", "Accepted locations");
        m.put("max_notice_days", "Longest notice period (days)");
        m.put("max_ctc_lpa", "Highest CTC (LPA)");
        m.put("shortlist_confirmed_at", "Shortlist confirmed");
        m.put("name", "Name");
        m.put("kind", "Type");
        m.put("mode", "Format");
        m.put("duration_min", "Length (minutes)");
        m.put("target_days", "Target time (days)");
        m.put("round", "Round");
        m.put("value", "Skill");
        m.put("min_years", "Minimum years");
        m.put("retired_at", "Removed");
        m.put("full_name", "Name");
        m.put("email", "Email");
        m.put("mobile", "Phone");
        m.put("city", "City");
        m.put("location_text", "Location");
        m.put("location_source", "Location from");
        m.put("candidate_location", "Location given in the AI interview");
        m.put("centre_note", "Why this partner assessment centre");
        m.put("country", "Country");
        m.put("stage", "Stage");
        m.put("cv_status", "CV");
        m.put("cv_delete_after", "CV deleted after");
        m.put("source", "Source");
        m.put("source_detail", "Source detail");
        m.put("decision", "Decision");
        m.put("reason", "Reason");
        m.put("decided_at", "Decided");
        m.put("challenged_at", "Challenged");
        m.put("body", "Note");
        m.put("start_at", "Interview time");
        m.put("candidate_tz", "Candidate's time zone");
        m.put("reply_by", "Reply by");
        m.put("invited_at", "Invitation sent");
        m.put("arrive_by", "Arrive by");
        m.put("meeting_link", "Meeting link");
        m.put("venue", "Venue");
        m.put("assessor_id", "Assessor");
        m.put("scheduled_by", "Booked by");
        m.put("completed_at", "Completed");
        m.put("employee_id", "Employee ID");
        m.put("notified_at", "Code emailed");
        m.put("skill_scores", "Skill scores");
        m.put("measure_scores", "General scores");
        m.put("strengths", "Strengths");
        m.put("concerns", "Concerns");
        m.put("questions", "Questions asked");
        m.put("summary", "Summary");
        m.put("next_round_notes", "Notes for the next round");
        m.put("recommendation", "Recommendation");
        m.put("overall_score", "Overall score");
        m.put("submitted_at", "Sent");
        m.put("note", "Note");
        m.put("available_days", "Days free");
        m.put("availability", "Dates free");
        m.put("time_of_day", "Time of day");
        m.put("new_start_at", "New time");
        m.put("resolved_by", "Answered by");
        m.put("requested_by_candidate", "Asked by the candidate");
        m.put("status_reason", "Reason");
        m.put("applications.status", "Outcome");
        m.put("interviews.status", "Interview status");
        return m;
    }
}
