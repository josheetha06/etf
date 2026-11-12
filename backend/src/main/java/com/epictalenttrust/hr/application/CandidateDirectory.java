package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.screening.AiSuggestion;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.shortlist.ShortlistDecisionType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Every candidate in the company's jobs: search, filters, sorting and pages. One row per candidate per job. */
@Component
public class CandidateDirectory {

    public record Filter(String query, UUID jobId, ApplicationStage stage, ApplicationStatus status,
                         AiSuggestion suggestion, String decision, ApplicationSource source, String country,
                         LocalDate addedFrom, LocalDate addedTo, boolean needsDecision, String action, String sort,
                         int page, int size) {
    }

    public record Entry(UUID applicationId, UUID candidateId, String candidateRef, String candidateName, String email,
                        String city, String country, UUID jobId, String jobTitle, String jobRef, ApplicationStage stage,
                        ApplicationStatus status, Integer matchScore, AiSuggestion suggestion,
                        ShortlistDecisionType decision, ApplicationSource source, String sourceDetail, Instant addedAt,
                        String nextAction, int openRedFlags, int flags, Integer currentRound, String roundName,
                        Instant dueAt, boolean overdue, com.epictalenttrust.hr.job.JobDtos.Changes changes,
                        com.epictalenttrust.hr.candidate.LocationSummary location) {

        Entry withChanges(com.epictalenttrust.hr.job.JobDtos.Changes c) {
            return new Entry(applicationId, candidateId, candidateRef, candidateName, email, city, country, jobId, jobTitle,
                    jobRef, stage, status, matchScore, suggestion, decision, source, sourceDetail, addedAt, nextAction,
                    openRedFlags, flags, currentRound, roundName, dueAt, overdue, c, location);
        }
    }

    public record Page(List<Entry> items, long total, int page, int size) {
    }

    /** "Needs action": overdue first, then decisions to make, then interviews to book, then everyone else. */
    private static final String BY_ACTION = """
            case when status <> 'active' then 9
                 when due_at < now() then 0
                 when next_action in ('decide_round', 'decide_shortlist', 'record_offer') then 1
                 when next_action in ('answer_reschedule', 'schedule_round') then 2
                 when next_action = 'review_flags' then 3
                 when next_action = 'waiting' then 4
                 else 5 end, due_at asc nulls last, added_at desc""";

    private static final Map<String, String> SORTS = Map.of(
            "action", BY_ACTION,
            "added_desc", "added_at desc",
            "added_asc", "added_at asc",
            "name", "lower(full_name) asc, added_at desc",
            "score_desc", "match_score desc nulls last, added_at desc",
            "score_asc", "match_score asc nulls first, added_at desc",
            "stage", "array_position(array['screening','shortlist','round1','round2','round3','round4','round5','round6','round7','round8','offer','closed']::text[], stage::text) desc, added_at desc",
            "due", "due_at asc nulls last, added_at desc",
            "job", "lower(job_title) asc, match_score desc nulls last");

    private static final String WHERE = """
             where (cast(:pattern as text) is null
                    or lower(full_name) like :pattern or lower(coalesce(email, '')) like :pattern
                    or regexp_replace(coalesce(mobile, ''), '\\D', '', 'g') like :digits
                    or lower(candidate_ref) like :pattern or lower(job_title) like :pattern
                    or lower(job_ref) like :pattern or lower(coalesce(city, '')) like :pattern
                    or lower(coalesce(location_text, '')) like :pattern or lower(coalesce(location_place, '')) like :pattern
                    or lower(coalesce(location_district, '')) like :pattern)
               and (cast(:job as uuid) is null or job_id = :job)
               and (cast(:stage as text) is null or stage::text = :stage)
               and (cast(:status as text) is null or status::text = :status)
               and (cast(:suggestion as text) is null or suggestion::text = :suggestion)
               and (cast(:decision as text) is null
                    or (:decision = 'none' and shortlist_decision is null)
                    or shortlist_decision::text = :decision)
               and (cast(:source as text) is null or source::text = :source)
               and (cast(:country as text) is null or lower(coalesce(country, '')) = :country)
               and (cast(:addedFrom as date) is null or added_at >= cast(:addedFrom as date))
               and (cast(:addedTo as date) is null or added_at < cast(:addedTo as date) + 1)
               and (not :needsDecision or next_action in
            """ + PipelineQueries.DECISION_ACTIONS + """
            )
               and (cast(:action as text) is null or next_action = :action)
               and (not :overdueOnly or due_at < now())
            """;

    private final JdbcClient jdbc;
    private final com.epictalenttrust.hr.security.Access access;
    private final com.epictalenttrust.hr.common.ChangeLog changeLog;

    public CandidateDirectory(JdbcClient jdbc, com.epictalenttrust.hr.security.Access access,
                              com.epictalenttrust.hr.common.ChangeLog changeLog) {
        this.jdbc = jdbc;
        this.access = access;
        this.changeLog = changeLog;
    }

    @Transactional(readOnly = true)
    public Page search(Filter filter) {
        int size = Math.min(Math.max(filter.size(), 1), 100);
        int page = Math.max(filter.page(), 0);
        String orderBy = SORTS.getOrDefault(filter.sort() == null ? "action" : filter.sort(), BY_ACTION);
        long total = bind(jdbc.sql(PipelineQueries.PIPELINE + "select count(*) from pipeline" + WHERE), filter)
                .query(Long.class).single();
        List<Entry> items = bind(jdbc.sql(PipelineQueries.PIPELINE + "select * from pipeline" + WHERE
                        + " order by " + orderBy + " limit :limit offset :offset"), filter)
                .param("limit", size).param("offset", (long) page * size)
                .query((rs, row) -> entry(rs))
                .list();
        // Last updated: the later of the candidate's details and this application.
        List<UUID> ids = new java.util.ArrayList<>();
        items.forEach(e -> {
            ids.add(e.applicationId());
            ids.add(e.candidateId());
        });
        var changes = changeLog.forRecords(List.of("applications", "candidates"), ids);
        items = items.stream().map(e -> {
            var app = changes.get(e.applicationId());
            var cand = changes.get(e.candidateId());
            if (app == null) {
                return e.withChanges(cand);
            }
            if (cand != null && cand.lastUpdatedAt() != null && app.lastUpdatedAt() != null
                    && cand.lastUpdatedAt().isAfter(app.lastUpdatedAt())) {
                return e.withChanges(new com.epictalenttrust.hr.job.JobDtos.Changes(app.createdByName(), app.createdAt(),
                        cand.lastUpdatedByName(), cand.lastUpdatedAt()));
            }
            return e.withChanges(app);
        }).toList();
        return new Page(items, total, page, size);
    }

    private JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Filter f) {
        String query = f.query() == null || f.query().isBlank() ? null : f.query().strip().toLowerCase(Locale.ROOT);
        String digits = query == null ? null : query.replaceAll("\\D", "");
        return access.bind(spec)
                .param("overdueOnly", "overdue".equals(f.action()))
                .param("pattern", query == null ? null : "%" + query + "%")
                .param("digits", digits == null || digits.length() < 4 ? "~" : "%" + digits + "%")
                .param("job", f.jobId())
                .param("stage", f.stage() == null ? null : f.stage().name().toLowerCase(Locale.ROOT))
                .param("status", f.status() == null ? null : f.status().name().toLowerCase(Locale.ROOT))
                .param("suggestion", f.suggestion() == null ? null : f.suggestion().name().toLowerCase(Locale.ROOT))
                .param("decision", f.decision() == null || f.decision().isBlank() ? null : f.decision().toLowerCase(Locale.ROOT))
                .param("source", f.source() == null ? null : f.source().name().toLowerCase(Locale.ROOT))
                .param("country", f.country() == null || f.country().isBlank() ? null : f.country().strip().toLowerCase(Locale.ROOT))
                .param("addedFrom", f.addedFrom())
                .param("addedTo", f.addedTo())
                .param("needsDecision", f.needsDecision())
                .param("action", f.action() == null || f.action().isBlank() || "overdue".equals(f.action()) ? null : f.action());
    }

    static Entry entry(ResultSet rs) throws SQLException {
        return new Entry(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "candidate_id"), rs.getString("candidate_ref"),
                rs.getString("full_name"), rs.getString("email"), rs.getString("city"), rs.getString("country"),
                Rows.uuid(rs, "job_id"), rs.getString("job_title"), rs.getString("job_ref"),
                Rows.enumOf(rs, "stage", ApplicationStage.class), Rows.enumOf(rs, "status", ApplicationStatus.class),
                Rows.integer(rs, "match_score"), Rows.enumOf(rs, "suggestion", AiSuggestion.class),
                Rows.enumOf(rs, "shortlist_decision", ShortlistDecisionType.class),
                Rows.enumOf(rs, "source", ApplicationSource.class), rs.getString("source_detail"),
                Rows.instant(rs, "added_at"), rs.getString("next_action"), rs.getInt("red_open"), rs.getInt("flags_total"),
                Rows.integer(rs, "current_round"), rs.getString("round_name"), Rows.instant(rs, "due_at"),
                rs.getTimestamp("due_at") != null && rs.getTimestamp("due_at").toInstant().isBefore(Instant.now()), null,
                com.epictalenttrust.hr.candidate.LocationSummary.from(rs));
    }
}
