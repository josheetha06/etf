package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Everything that happened to a candidate in one job, newest first, in plain words. */
@Service
public class TimelineService {

    public record Event(Instant at, String actorKind, String actorName, String action, String text) {
    }

    private final Tenancy tenancy;
    private final JdbcClient jdbc;

    public TimelineService(Tenancy tenancy, JdbcClient jdbc) {
        this.tenancy = tenancy;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Event> timeline(UUID applicationId) {
        tenancy.application(applicationId);
        return jdbc.sql("""
                        select l.at, l.actor_kind, l.action, i.round,
                               coalesce(u.full_name, s.full_name) as actor_name
                          from audit_log l
                          left join interviews i on l.record_type = 'interview' and i.id = l.record_id
                          left join hr_users u on l.actor_kind = 'hr_user' and u.id = l.actor_id
                          left join ett_staff s on l.actor_kind = 'ett_staff' and s.id = l.actor_id
                         where (l.record_type = 'application' and l.record_id = :id)
                            or (l.record_type = 'interview' and i.application_id = :id)
                         order by l.at desc limit 200
                        """)
                .param("id", applicationId)
                .query((rs, row) -> {
                    String kind = rs.getString("actor_kind");
                    String name = "report.sample_created".equals(rs.getString("action")) ? "Sample data" : switch (kind) {
                        case "system" -> "AI agent";
                        case "candidate" -> "Candidate";
                        default -> rs.getString("actor_name") == null ? "Someone" : rs.getString("actor_name");
                    };
                    Integer round = Rows.integer(rs, "round");
                    return new Event(Rows.instant(rs, "at"), kind, name, rs.getString("action"),
                            describe(rs.getString("action"), round));
                })
                .list();
    }

    static String describe(String action, Integer round) {
        String r = round == null ? "" : "Round " + round + " ";
        return switch (action) {
            case "cv.added" -> "added the CV";
            case "screening.completed" -> "scored the CV";
            case "shortlist.decided" -> "recorded a shortlist decision";
            case "flag.reviewed" -> "reviewed a red flag";
            case "match.flagged" -> "reported a wrong match";
            case "note.added" -> "added a note";
            case "keyfacts.updated" -> "updated key facts";
            case "candidate.updated" -> "corrected the candidate's details";
            case "application.withdrawn" -> "recorded that the candidate withdrew";
            case "application.copied" -> "added the candidate from another job";
            case "message.sent" -> "sent a message";
            case "interview.scheduled" -> "set the " + r + "interview time";
            case "interview.invited" -> "sent the " + r + "invitation";
            case "interview.accepted" -> "recorded that the candidate accepted the " + r + "time";
            case "interview.cancelled" -> "cancelled the " + r + "interview";
            case "interview.completed" -> "completed the " + r + "interview";
            case "interview.no_show" -> "recorded a no-show at the " + r + "interview";
            case "reschedule.requested" -> "logged a request for a different " + r + "time";
            case "reschedule.new_time_sent" -> "approved a new " + r + "time";
            case "reschedule.kept_original" -> "kept the original " + r + "time";
            case "report.released" -> "released the " + r + "report";
            case "report.submitted" -> "submitted the " + r + "report";
            case "report.recheck_requested" -> "asked ETT to re-check the " + r + "report";
            case "report.sample_created" -> "added a sample " + r + "report";
            case "panel.accepted" -> "accepted a place on the " + r + "panel";
            case "panel.declined" -> "declined a place on the " + r + "panel";
            case "feedback.submitted" -> "submitted " + r + "panel feedback";
            case "stage.decided" -> "recorded a decision";
            case "stage.challenged" -> "logged a challenge to a decision";
            case "stage.decision_undone" -> "undid a decision (the candidate's email was cancelled)";
            case "interview.cancelled_job_closed" -> "cancelled the " + r + "interview because the job closed";
            case "application.position_closed" -> "told the candidate the position has closed";
            case "offer.accepted" -> "recorded that the offer was accepted";
            case "offer.declined" -> "recorded that the offer was turned down";
            default -> action.replace('.', ' ').replace('_', ' ');
        };
    }
}
