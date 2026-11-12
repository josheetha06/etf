package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.application.ApplicationSource;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** CV intake: upload many CVs, follow their progress, finish ones that need details, or add one candidate. */
@RestController
public class CvIntakeController {

    public record CompleteRequest(@Size(max = 150) String fullName, @Size(max = 200) String email,
                                  @Size(max = 40) String mobile, @Size(max = 80) String city, @Size(max = 80) String country,
                                  ApplicationSource source, @Size(max = 200) String sourceDetail) {
    }

    private final Tenancy tenancy;
    private final CvIntakeService intake;
    private final CvIntakeQueries queries;
    private final CvUploadRepository uploads;

    public CvIntakeController(Tenancy tenancy, CvIntakeService intake, CvIntakeQueries queries, CvUploadRepository uploads) {
        this.tenancy = tenancy;
        this.intake = intake;
        this.queries = queries;
        this.uploads = uploads;
    }

    @PostMapping(path = "/api/jobs/{jobId}/cvs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @CanManageHiring
    public List<CvIntakeQueries.UploadRow> upload(@PathVariable UUID jobId,
                                                  @RequestParam("files") List<MultipartFile> files,
                                                  @RequestParam(value = "source", required = false) ApplicationSource source,
                                                  @RequestParam(value = "sourceDetail", required = false) String sourceDetail,
                                                  @RequestParam(value = "batchId", required = false) UUID batchId) {
        if (sourceDetail != null && sourceDetail.length() > 200) {
            throw ApiException.badRequest("source_detail_too_long", "Keep the source detail under 200 characters.");
        }
        return queries.byIds(intake.enqueue(jobId, files, source, sourceDetail, batchId));
    }

    @GetMapping("/api/jobs/{jobId}/cv-uploads")
    public List<CvIntakeQueries.UploadRow> list(@PathVariable UUID jobId,
                                                @RequestParam(value = "batch", required = false) UUID batchId,
                                                @RequestParam(value = "open", defaultValue = "false") boolean openOnly) {
        tenancy.job(jobId);
        return queries.forJob(jobId, batchId, openOnly);
    }

    /** "Add candidate", step one: read the CV now and return the details found, for HR to check. */
    @PostMapping(path = "/api/jobs/{jobId}/candidate-drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @CanManageHiring
    public CvIntakeQueries.UploadRow draft(@PathVariable UUID jobId, @RequestParam("file") MultipartFile file,
                                           @RequestParam(value = "source", required = false) ApplicationSource source,
                                           @RequestParam(value = "sourceDetail", required = false) String sourceDetail) {
        CvUpload upload = intake.draft(jobId, file, source, sourceDetail);
        return queries.byId(upload.getId()).orElseThrow();
    }

    /** Finish a CV that needs details, or step two of "Add candidate". */
    @PostMapping("/api/cv-uploads/{uploadId}/complete")
    @CanManageHiring
    public CvIntakeQueries.UploadRow complete(@PathVariable UUID uploadId, @Valid @RequestBody CompleteRequest body) {
        CvUpload upload = uploads.findById(uploadId).orElseThrow(() -> ApiException.notFound("Upload"));
        tenancy.job(upload.getJobId());
        intake.complete(uploadId, new CvIntakeService.CandidateInput(body.fullName(), body.email(), body.mobile(),
                body.city(), body.country(), body.source(), body.sourceDetail()));
        return queries.byId(uploadId).orElseThrow();
    }

    @PostMapping("/api/cv-uploads/{uploadId}/dismiss")
    @CanManageHiring
    public CvIntakeQueries.UploadRow dismiss(@PathVariable UUID uploadId) {
        CvUpload upload = uploads.findById(uploadId).orElseThrow(() -> ApiException.notFound("Upload"));
        tenancy.job(upload.getJobId());
        intake.dismiss(uploadId);
        return queries.byId(uploadId).orElseThrow();
    }
}
