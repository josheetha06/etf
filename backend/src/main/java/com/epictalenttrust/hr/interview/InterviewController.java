package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.interview.InterviewDtos.CancelInput;
import com.epictalenttrust.hr.interview.InterviewDtos.InterviewRow;
import com.epictalenttrust.hr.interview.InterviewDtos.RescheduleDetail;
import com.epictalenttrust.hr.interview.InterviewDtos.RescheduleInput;
import com.epictalenttrust.hr.interview.InterviewDtos.RespondInput;
import com.epictalenttrust.hr.interview.InterviewDtos.Round1Plan;
import com.epictalenttrust.hr.interview.InterviewDtos.Round1Request;
import com.epictalenttrust.hr.interview.InterviewDtos.SaveResult;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
public class InterviewController {

    private final SchedulingService scheduling;
    private final InterviewService interviews;

    public InterviewController(SchedulingService scheduling, InterviewService interviews) {
        this.scheduling = scheduling;
        this.interviews = interviews;
    }

    /** Booking AI interviews for several candidates at once (the job's first AI round, or the one asked for). */
    @GetMapping("/api/jobs/{jobId}/ai-interview-plan")
    public Round1Plan plan(@PathVariable UUID jobId, @RequestParam(required = false) Integer round,
                           @RequestParam(required = false) String zone) {
        return scheduling.plan(jobId, round, zone);
    }

    @PutMapping("/api/jobs/{jobId}/ai-interviews")
    @CanManageHiring
    public SaveResult saveDrafts(@PathVariable UUID jobId, @RequestParam(required = false) Integer round,
                                 @Valid @RequestBody Round1Request request) {
        return scheduling.save(jobId, round, request, false);
    }

    @PostMapping("/api/jobs/{jobId}/ai-interviews/send")
    @CanManageHiring
    public SaveResult send(@PathVariable UUID jobId, @RequestParam(required = false) Integer round,
                           @Valid @RequestBody Round1Request request) {
        return scheduling.save(jobId, round, request, true);
    }

    @GetMapping("/api/interviews")
    public List<InterviewRow> list(@RequestParam(defaultValue = "upcoming") String view,
                                   @RequestParam(required = false) UUID jobId) {
        return interviews.list(InterviewQueries.View.valueOf(view.toUpperCase(Locale.ROOT)), jobId);
    }

    @PostMapping("/api/interviews/{interviewId}/accept")
    @CanManageHiring
    public InterviewRow accept(@PathVariable UUID interviewId) {
        return interviews.accept(interviewId);
    }

    @PostMapping("/api/interviews/{interviewId}/cancel")
    @CanManageHiring
    public InterviewRow cancel(@PathVariable UUID interviewId, @Valid @RequestBody(required = false) CancelInput body) {
        return interviews.cancel(interviewId, body);
    }

    @PostMapping("/api/interviews/{interviewId}/reschedule-requests")
    @CanManageHiring
    public RescheduleDetail logRequest(@PathVariable UUID interviewId, @Valid @RequestBody RescheduleInput body) {
        return interviews.logRequest(interviewId, body);
    }

    @GetMapping("/api/reschedule-requests/{requestId}")
    public RescheduleDetail request(@PathVariable UUID requestId, @RequestParam(required = false) String zone) {
        return interviews.detail(requestId, zone);
    }

    @PostMapping("/api/reschedule-requests/{requestId}/respond")
    @CanManageHiring
    public RescheduleDetail respond(@PathVariable UUID requestId, @Valid @RequestBody RespondInput body) {
        return interviews.respond(requestId, body);
    }
}
