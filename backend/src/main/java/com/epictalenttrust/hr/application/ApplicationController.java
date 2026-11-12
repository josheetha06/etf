package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.activity.ProcessingLog;
import com.epictalenttrust.hr.application.ApplicationDtos.ApplicationRow;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.report.DecisionService;
import com.epictalenttrust.hr.report.ReportDtos.DecisionInput;
import com.epictalenttrust.hr.report.ReportDtos.StageDecisionView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import com.epictalenttrust.hr.security.CanDecide;
import com.epictalenttrust.hr.security.CanManageHiring;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.storage.FileStorage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
public class ApplicationController {

    private final Tenancy tenancy;
    private final ApplicationQueries queries;
    private final FileStorage storage;
    private final CandidateRepository candidates;
    private final ProcessingLog processingLog;
    private final ApplicationDetailService details;
    private final DecisionService decisions;

    public ApplicationController(Tenancy tenancy, ApplicationQueries queries, FileStorage storage, CandidateRepository candidates, ProcessingLog processingLog,
                                 ApplicationDetailService details,
                                 DecisionService decisions) {
        this.tenancy = tenancy;
        this.queries = queries;
        this.storage = storage;
        this.candidates = candidates;
        this.processingLog = processingLog;
        this.details = details;
        this.decisions = decisions;
    }

    @GetMapping("/api/applications/{applicationId}")
    public ApplicationDetailService.ApplicationDetail detail(@PathVariable UUID applicationId) {
        return details.detail(applicationId);
    }

    public record LocationInput(@jakarta.validation.constraints.Size(max = 200) String text) {
    }

    /** HR sets or corrects where the candidate is (a town, city or PIN code). */
    @org.springframework.web.bind.annotation.PutMapping("/api/applications/{applicationId}/location")
    @com.epictalenttrust.hr.security.CanManageHiring
    public ApplicationDetailService.CandidateLocation setLocation(@PathVariable UUID applicationId,
                                                                  @Valid @RequestBody LocationInput body) {
        return details.setLocation(applicationId, body.text());
    }

    @PostMapping("/api/applications/{applicationId}/stage-decisions")
    @CanDecide
    public List<StageDecisionView> decide(@PathVariable UUID applicationId, @Valid @RequestBody DecisionInput body) {
        return decisions.decide(applicationId, body);
    }

    /** Undo a round decision in its first 5 minutes; the candidate's email about it is cancelled. */
    @PostMapping("/api/stage-decisions/{decisionId}/undo")
    @CanDecide
    public List<StageDecisionView> undo(@PathVariable UUID decisionId) {
        return decisions.undo(decisionId);
    }

    @PostMapping("/api/stage-decisions/{decisionId}/challenge")
    @CanManageHiring
    public List<StageDecisionView> challenge(@PathVariable UUID decisionId) {
        return decisions.recordChallenge(decisionId);
    }

    @GetMapping("/api/jobs/{jobId}/applications")
    public List<ApplicationRow> forJob(@PathVariable UUID jobId) {
        tenancy.job(jobId);
        return queries.forJob(jobId);
    }

    @GetMapping("/api/applications/{applicationId}/cv")
    public ResponseEntity<byte[]> downloadCv(@PathVariable UUID applicationId, HttpServletRequest request) {
        JobApplication application = tenancy.application(applicationId);
        if (application.getCvFileKey() == null) {
            throw ApiException.notFound("CV file");
        }
        byte[] content = storage.get(application.getCvFileKey());
        processingLog.documentAccess(applicationId, application.getCvFileKey(), ProcessingLog.DocAction.DOWNLOAD,
                "hr_user", CurrentUser.id(), request.getRemoteAddr());
        String candidateName = candidates.findById(application.getCandidateId()).map(c -> c.getFullName()).orElse("candidate");
        String extension = application.getCvFileKey().substring(application.getCvFileKey().lastIndexOf('.') + 1);
        String fileName = candidateName.replaceAll("[^A-Za-z0-9 ]", "").replace(' ', '_') + "_CV." + extension;
        return ResponseEntity.ok()
                .contentType(switch (extension) {
                    case "pdf" -> MediaType.APPLICATION_PDF;
                    case "docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                    default -> MediaType.parseMediaType("application/msword");
                })
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }
}
