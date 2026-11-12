package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.job.JobDtos.JobDetail;
import com.epictalenttrust.hr.job.JobDtos.JobRequest;
import com.epictalenttrust.hr.job.JobDtos.JobSummary;
import com.epictalenttrust.hr.security.CanManageHiring;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobs;
    private final JobDescriptionReader descriptionReader;
    private final com.epictalenttrust.hr.cv.CvTextExtractor textExtractor;

    public JobController(JobService jobs, JobDescriptionReader descriptionReader,
                         com.epictalenttrust.hr.cv.CvTextExtractor textExtractor) {
        this.jobs = jobs;
        this.descriptionReader = descriptionReader;
        this.textExtractor = textExtractor;
    }

    @GetMapping
    public List<JobSummary> list(@RequestParam(value = "q", required = false) String query,
                                 @RequestParam(value = "status", required = false) String status) {
        return jobs.list(query, status);
    }

    /** Live advice on criteria while HR types: tidy names, what each also matches, warnings. */
    @PostMapping("/criteria-check")
    public JobDtos.CriteriaCheck checkCriteria(@Valid @RequestBody JobDtos.CriteriaCheckRequest request) {
        return jobs.check(request.criteria());
    }

    @GetMapping("/{jobId}")
    public JobDetail get(@PathVariable UUID jobId) {
        return jobs.detail(jobId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @CanManageHiring
    public JobDetail create(@Valid @RequestBody JobRequest request) {
        return jobs.create(request);
    }

    @PutMapping("/{jobId}")
    @CanManageHiring
    public JobDetail update(@PathVariable UUID jobId, @Valid @RequestBody JobRequest request) {
        return jobs.update(jobId, request);
    }

    @PostMapping("/{jobId}/publish")
    @CanManageHiring
    public JobDetail publish(@PathVariable UUID jobId) {
        return jobs.publish(jobId);
    }

    @PostMapping("/{jobId}/close")
    @CanManageHiring
    public JobDetail close(@PathVariable UUID jobId) {
        return jobs.close(jobId);
    }

    public record StarState(boolean starred) {
    }

    /** Star a job for yourself (other people don't see your stars). */
    @org.springframework.web.bind.annotation.PutMapping("/{jobId}/star")
    public StarState star(@PathVariable UUID jobId) {
        return new StarState(jobs.star(jobId, true));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{jobId}/star")
    public StarState unstar(@PathVariable UUID jobId) {
        return new StarState(jobs.star(jobId, false));
    }

    /** What closing the job would do, for the close dialog. */
    @GetMapping("/{jobId}/close-impact")
    public com.epictalenttrust.hr.interview.JobCloseOut.Impact closeImpact(@PathVariable UUID jobId) {
        return jobs.closeImpact(jobId);
    }

    /** Put on hold, close, or open again (with a reason for hold and close). */
    @PostMapping("/{jobId}/status")
    @CanManageHiring
    public JobDetail changeStatus(@PathVariable UUID jobId, @Valid @RequestBody JobDtos.StatusChange body) {
        return jobs.changeStatus(jobId, body);
    }

    /** The company's default round plan, to pre-fill a new job. */
    @GetMapping("/default-plan")
    public List<JobDtos.RoundView> defaultPlan() {
        return jobs.defaultPlan();
    }

    /** Who can own a job or be its hiring manager. */
    @GetMapping("/people")
    public List<JobService.PersonOption> people() {
        return jobs.people();
    }

    /** Reads an uploaded job description (PDF or Word) and suggests skills from it. Nothing is saved. */
    @PostMapping(path = "/description/read", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @CanManageHiring
    public JobDtos.DescriptionRead readDescription(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        if (file.isEmpty()) {
            throw com.epictalenttrust.hr.common.ApiException.badRequest("empty_file", "That file is empty.");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw com.epictalenttrust.hr.common.ApiException.badRequest("file_too_large",
                    "The job description is over 5 MB. Save it as a smaller PDF or Word file, or paste the text.");
        }
        String text;
        try {
            text = textExtractor.extract(file.getBytes(), file.getOriginalFilename()).text();
        } catch (com.epictalenttrust.hr.common.ApiException ex) {
            throw "no_text".equals(ex.getCode())
                    ? com.epictalenttrust.hr.common.ApiException.badRequest("no_text",
                            "We couldn't read any text in that file. If it's a scan, paste the text instead.")
                    : ex;
        }
        if (text == null || text.strip().length() < 30) {
            throw com.epictalenttrust.hr.common.ApiException.badRequest("no_text",
                    "We couldn't read any text in that file. If it's a scan, paste the text instead.");
        }
        text = text.strip().replaceAll("\n{3,}", "\n\n");
        if (text.length() > 20000) {
            text = text.substring(0, 20000);
        }
        JobDescriptionReader.Suggestions s = descriptionReader.suggest(
                com.epictalenttrust.hr.security.CurrentUser.companyId(), text);
        return new JobDtos.DescriptionRead(text, file.getOriginalFilename(), s.mustHave(), s.niceToHave());
    }

    /** Suggests skills from pasted job description text. */
    @PostMapping("/description/suggest")
    public JobDtos.DescriptionRead suggestFromText(@Valid @RequestBody JobDtos.DescriptionText body) {
        JobDescriptionReader.Suggestions s = descriptionReader.suggest(
                com.epictalenttrust.hr.security.CurrentUser.companyId(), body.text());
        return new JobDtos.DescriptionRead(null, null, s.mustHave(), s.niceToHave());
    }
}
