package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.application.PipelineQueries;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.interview.RoundSchedulingService.MemberInput;
import com.epictalenttrust.hr.routing.CentreOptionsService;
import com.epictalenttrust.hr.sample.SampleReportService;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Booking each candidate's current round (AI interview, partner assessor or panel), from any page. */
@RestController
public class RoundController {

    public record ReadyRow(UUID applicationId, UUID jobId, String jobTitle, String jobRef, String candidateName,
                           String candidateRef, String city, int round, String roundName,
                           com.epictalenttrust.hr.job.RoundKind roundKind, Integer matchScore, Instant readySince,
                           Instant dueAt, com.epictalenttrust.hr.candidate.LocationSummary location) {
    }

    public record PanelUpdate(@jakarta.validation.constraints.NotEmpty
                              @jakarta.validation.constraints.Size(max = 12)
                              List<@Valid MemberInput> panel) {
    }

    private final RoundSchedulingService scheduling;
    private final InterviewService interviews;
    private final SampleReportService samples;
    private final CentreOptionsService centreOptions;
    private final com.epictalenttrust.hr.security.Access access;
    private final JdbcClient jdbc;

    public RoundController(RoundSchedulingService scheduling, InterviewService interviews, SampleReportService samples,
                           CentreOptionsService centreOptions, com.epictalenttrust.hr.security.Access access,
                           JdbcClient jdbc) {
        this.scheduling = scheduling;
        this.interviews = interviews;
        this.samples = samples;
        this.centreOptions = centreOptions;
        this.access = access;
        this.jdbc = jdbc;
    }

    @PostMapping("/api/interviews/{interviewId}/no-show")
    @CanManageHiring
    public InterviewRow noShow(@PathVariable UUID interviewId) {
        return interviews.markNoShow(interviewId);
    }

    /** Fills in a sample report (labelled SAMPLE) when sample reports are on for this installation. */
    @PostMapping("/api/interviews/{interviewId}/sample-report")
    @CanManageHiring
    public ResponseEntity<Void> sampleReport(@PathVariable UUID interviewId) {
        samples.completeNow(interviewId);
        return ResponseEntity.noContent().build();
    }

    /** Candidates whose next step is to book a round, oldest first. */
    @GetMapping("/api/interviews/ready-to-schedule")
    @Transactional(readOnly = true)
    public List<ReadyRow> ready(@RequestParam(required = false) UUID jobId, @RequestParam(required = false) Integer round) {
        return access.bind(jdbc.sql(PipelineQueries.PIPELINE + """
                        select p.application_id, p.job_id, p.job_title, p.job_ref, p.full_name, p.candidate_ref, p.city,
                               p.current_round, p.round_name, p.round_kind, p.match_score, p.due_at,
                               coalesce(p.stage_entered_at, a.last_modified_at) as ready_since,
                               p.location_text, p.location_place, p.location_district, p.location_region, p.location_pin, p.location_source, p.location_placed, p.ai_round_ahead
                          from pipeline p join applications a on a.id = p.application_id
                         where p.next_action = 'schedule_round'
                           and (cast(:jobId as uuid) is null or p.job_id = :jobId)
                           and (cast(:round as int) is null or p.current_round = :round)
                         order by p.due_at asc nulls last, ready_since asc nulls last, p.full_name
                         limit 500
                        """))
                .param("jobId", jobId).param("round", round)
                .query((rs, row) -> new ReadyRow(Rows.uuid(rs, "application_id"), Rows.uuid(rs, "job_id"),
                        rs.getString("job_title"), rs.getString("job_ref"), rs.getString("full_name"),
                        rs.getString("candidate_ref"), rs.getString("city"), rs.getInt("current_round"),
                        rs.getString("round_name"), Rows.enumOf(rs, "round_kind", com.epictalenttrust.hr.job.RoundKind.class),
                        Rows.integer(rs, "match_score"), Rows.instant(rs, "ready_since"), Rows.instant(rs, "due_at"),
                        com.epictalenttrust.hr.candidate.LocationSummary.from(rs)))
                .list();
    }

    /** The candidate's current round as the job's plan describes it (name, type, length). */
    @GetMapping("/api/applications/{applicationId}/current-round")
    public RoundSchedulingService.CurrentRound currentRound(@PathVariable UUID applicationId) {
        return scheduling.currentRound(applicationId);
    }

    @PostMapping("/api/applications/{applicationId}/schedule/ai-interview")
    @CanManageHiring
    public InterviewRow scheduleAi(@PathVariable UUID applicationId,
                                   @Valid @RequestBody RoundSchedulingService.AiInput input) {
        return scheduling.scheduleAi(applicationId, input);
    }

    /**
     * Partner assessment centres for this candidate: the nearest centre city's centres taking turns, optionally for a
     * preferred day and time; with otherCities=true, the centres in other cities too (for exceptions).
     */
    @GetMapping("/api/applications/{applicationId}/partner-centres")
    public CentreOptionsService.CentreOptions centres(@PathVariable UUID applicationId,
                                                      @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate day,
                                                      @RequestParam(required = false) String timeOfDay,
                                                      @RequestParam(defaultValue = "false") boolean otherCities) {
        return centreOptions.options(applicationId, day, timeOfDay, otherCities);
    }

    @PostMapping("/api/applications/{applicationId}/schedule/partner")
    @CanManageHiring
    public InterviewRow schedulePartner(@PathVariable UUID applicationId,
                                        @Valid @RequestBody RoundSchedulingService.PartnerInput input) {
        return scheduling.schedulePartner(applicationId, input);
    }

    @PostMapping("/api/applications/{applicationId}/schedule/panel")
    @CanManageHiring
    public InterviewRow schedulePanel(@PathVariable UUID applicationId,
                                      @Valid @RequestBody RoundSchedulingService.PanelInput input) {
        return scheduling.schedulePanel(applicationId, input);
    }

    /** Change who is on a panel; new members (and changed email addresses) get the interview code by email. */
    @PutMapping("/api/interviews/{interviewId}/panel")
    @CanManageHiring
    public InterviewRow updatePanel(@PathVariable UUID interviewId, @Valid @RequestBody PanelUpdate body) {
        return scheduling.updatePanel(interviewId, body.panel());
    }
}
