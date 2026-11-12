package com.epictalenttrust.hr.cv;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.activity.ProcessingLog;
import com.epictalenttrust.hr.application.ApplicationSource;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.ErrorLogWriter;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.activity.ActorKind;
import com.epictalenttrust.hr.screening.AiScreening;
import com.epictalenttrust.hr.screening.ScreeningService;
import com.epictalenttrust.hr.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Takes CVs in and turns them into scored candidates. Uploads are checked at once (type, size, empty) and queued;
 * the worker reads and scores each one in the background, normally within seconds and always within 10 minutes.
 * A CV with no email address is kept as "needs details" for HR to complete instead of being refused.
 */
@Service
public class CvIntakeService {

    public static final int MAX_FILES_PER_REQUEST = 20;
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MAX_ATTEMPTS = 3;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Logger log = LoggerFactory.getLogger(CvIntakeService.class);

    /** Details HR typed or corrected for a candidate. */
    public record CandidateInput(String fullName, String email, String mobile, String city, String country,
                                 ApplicationSource source, String sourceDetail) {
    }

    /** Published when uploads are queued, so the worker starts as soon as they are saved. */
    public record Queued(UUID batchId) {
    }

    private final Tenancy tenancy;
    private final CvUploadRepository uploads;
    private final CvTextExtractor extractor;
    private final CandidateDetailsExtractor detailsExtractor;
    private final HiddenTextDetector hiddenText;
    private final ScreeningService screening;
    private final CandidateRepository candidates;
    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final FileStorage storage;
    private final ProcessingLog processingLog;
    private final ActivityService activity;
    private final ErrorLogWriter errorLog;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transaction;
    private final EttProperties properties;
    private final Clock clock;

    public CvIntakeService(Tenancy tenancy, CvUploadRepository uploads, CvTextExtractor extractor,
                           CandidateDetailsExtractor detailsExtractor, HiddenTextDetector hiddenText,
                           ScreeningService screening, CandidateRepository candidates,
                           JobApplicationRepository applications, JobRepository jobs, FileStorage storage,
                           ProcessingLog processingLog, ActivityService activity, ErrorLogWriter errorLog,
                           ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
                           EttProperties properties, Clock clock) {
        this.tenancy = tenancy;
        this.uploads = uploads;
        this.extractor = extractor;
        this.detailsExtractor = detailsExtractor;
        this.hiddenText = hiddenText;
        this.screening = screening;
        this.candidates = candidates;
        this.applications = applications;
        this.jobs = jobs;
        this.storage = storage;
        this.processingLog = processingLog;
        this.activity = activity;
        this.errorLog = errorLog;
        this.events = events;
        this.transaction = new TransactionTemplate(transactionManager);
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ upload and queue

    /** Checks and queues files. Returns their ids; refused files come back already marked with the reason. */
    @Transactional
    public List<UUID> enqueue(UUID jobId, List<MultipartFile> files, ApplicationSource source, String sourceDetail,
                              UUID batchId) {
        Job job = openJob(jobId);
        if (files == null || files.isEmpty()) {
            throw ApiException.badRequest("no_files", "Choose at least one CV.");
        }
        if (files.size() > MAX_FILES_PER_REQUEST) {
            throw ApiException.badRequest("too_many_files", "Send up to " + MAX_FILES_PER_REQUEST + " files at a time.");
        }
        UUID batch = batchId == null ? UUID.randomUUID() : batchId;
        List<UUID> ids = new ArrayList<>();
        for (MultipartFile file : files) {
            ids.add(store(job, batch, cleanName(file.getOriginalFilename()), bytes(file), source, sourceDetail,
                    CurrentUser.id()).getId());
        }
        events.publishEvent(new Queued(batch));
        activity.record("cv.uploaded", "job", jobId);
        return ids;
    }

    private CvUpload store(Job job, UUID batch, String fileName, byte[] content, ApplicationSource source,
                           String sourceDetail, UUID uploadedBy) {
        Instant now = clock.instant();
        CvUpload upload = uploads.save(new CvUpload(job.getId(), batch, fileName,
                source == null ? ApplicationSource.HR_UPLOAD : source, blankToNull(sourceDetail), uploadedBy, now));
        String extension = extension(fileName);
        if (content == null || content.length == 0) {
            upload.refuse("empty_file", "This file is empty.", now);
        } else if (content.length > MAX_BYTES) {
            upload.refuse("too_big", "Over 10 MB. Save it as a smaller PDF and try again.", now);
        } else if (!EXTENSIONS.contains(extension)) {
            upload.refuse("unsupported_file", "Only PDF or Word files (.pdf, .doc, .docx) can be added.", now);
        } else {
            String key = "intake/%s/%s/%s.%s".formatted(job.getCompanyId(), job.getId(), upload.getId(), extension);
            storage.put(key, content);
            upload.stored(key);
        }
        return upload;
    }

    // ------------------------------------------------------------------ background processing

    /** Reads and scores one claimed upload. Called by the worker, outside any transaction. */
    public void process(UUID uploadId) {
        CvUpload upload = uploads.findById(uploadId).orElse(null);
        if (upload == null || upload.getStatus() != CvUpload.Status.READING) {
            return;
        }
        Job job = jobs.findById(upload.getJobId()).orElseThrow();
        ActorContext.Actor actor = upload.getUploadedBy() == null ? ActorContext.Actor.SYSTEM
                : new ActorContext.Actor(ActorKind.HR_USER, upload.getUploadedBy());
        Instant started = clock.instant();
        try {
            ActorContext.runAs(actor, () -> read(upload, job, started));
        } catch (ApiException ex) {
            transaction.executeWithoutResult(status -> uploads.findById(uploadId).ifPresent(u -> {
                u.refuse(ex.getCode(), ex.getMessage(), clock.instant());
                deleteQuietly(u.getFileKey());
            }));
            processingLog.aiRun(ProcessingLog.AiTask.CV_PARSE, null, null, "apache-tika-4", false,
                    upload.getFileName() + ": " + ex.getMessage(), started, clock.instant());
        } catch (RuntimeException ex) {
            log.error("Could not process CV upload {} ({})", uploadId, upload.getFileName(), ex);
            errorLog.record("error", "cv-intake", ex, null);
            transaction.executeWithoutResult(status -> uploads.findById(uploadId).ifPresent(u -> {
                if (u.getAttempts() >= MAX_ATTEMPTS) {
                    u.refuse("processing_failed", "This CV couldn't be processed. Try uploading it again.", clock.instant());
                } else {
                    u.retryLater();
                }
            }));
        }
    }

    private void read(CvUpload upload, Job job, Instant started) {
        byte[] content = storage.get(upload.getFileKey());
        CvTextExtractor.Extracted extracted = extractor.extract(content, upload.getFileName());
        CandidateDetails details = detailsExtractor.extract(extracted.text(), upload.getFileName());
        String textKey = upload.getFileKey().replaceFirst("^intake/", "intake-text/").replaceFirst("\\.[a-z]+$", ".txt");
        storage.put(textKey, extracted.text().getBytes(StandardCharsets.UTF_8));
        String hidden = "pdf".equals(extracted.extension()) ? hiddenText.hiddenText(content).orElse(null) : null;
        processingLog.aiRun(ProcessingLog.AiTask.CV_PARSE, null, null, "apache-tika-4", true, null, started, clock.instant());
        transaction.executeWithoutResult(status -> {
            CvUpload current = uploads.findById(upload.getId()).orElseThrow();
            if (details.email() == null) {
                current.needsDetails(textKey, extracted.contentType(), details,
                        "No email address found in this CV. Add it to finish.", clock.instant());
                return;
            }
            addToJob(current, job, details, textKey, extracted.text(), hidden, current.getSource(), current.getSourceDetail());
        });
    }

    /** Creates (or reuses) the candidate, links them to the job and scores the CV. Runs inside a transaction. */
    private void addToJob(CvUpload upload, Job job, CandidateDetails details, String textKey, String text, String hidden,
                          ApplicationSource source, String sourceDetail) {
        Candidate candidate = candidates.findByEmail(details.email()).orElseGet(() ->
                candidates.save(new Candidate("ETT-%06d".formatted(candidates.nextRefNumber()),
                        details.fullName() == null ? nameFromEmail(details.email()) : details.fullName(), details.email())));
        candidate.fillMissing(details.mobile(), details.city(), details.country());
        var existing = applications.findByJobIdAndCandidateId(job.getId(), candidate.getId());
        if (existing.isPresent()) {
            upload.duplicate(existing.get().getId(), candidate.getFullName() + " is already in this job.", clock.instant());
            deleteQuietly(upload.getFileKey());
            return;
        }
        JobApplication application = applications.saveAndFlush(
                new JobApplication(job.getId(), candidate.getId(), source, upload.getUploadedBy()));
        application.setSourceDetail(sourceDetail);
        application.attachCv(upload.getFileKey(), textKey, LocalDate.now(clock).plusDays(properties.retention().cvDays()));
        if (job.isAiShortlisting()) {
            AiScreening result = screening.screen(application, job, new ScreeningService.CvText(text, details, hidden));
            log.debug("CV {} added to job {} with score {}", upload.getFileName(), job.getJobRef(), result.getMatchScore());
        } else {
            // No AI shortlisting for this job: the candidate goes straight to Round 1, whatever type it is.
            screening.readFactsOnly(application, new ScreeningService.CvText(text, details, hidden));
            application.moveTo(com.epictalenttrust.hr.application.ApplicationStage.ROUND1);
        }
        upload.added(application.getId(), details, clock.instant());
        activity.record("cv.added", "application", application.getId());
    }

    // ------------------------------------------------------------------ HR completes or adds one candidate

    /** HR adds the details for a CV that was waiting for them (or checked the details on the add-candidate form). */
    @Transactional
    public CvUpload complete(UUID uploadId, CandidateInput input) {
        CvUpload upload = uploads.findById(uploadId).orElseThrow(() -> ApiException.notFound("Upload"));
        Job job = openJob(upload.getJobId());
        if (upload.getStatus() != CvUpload.Status.NEEDS_DETAILS) {
            throw ApiException.conflict("already_done", "This CV has already been dealt with.");
        }
        String email = input.email() == null ? "" : input.email().strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
        }
        if (input.fullName() == null || input.fullName().isBlank()) {
            throw ApiException.badRequest("name_required", "Enter the candidate's name.");
        }
        CandidateDetails details = new CandidateDetails(input.fullName().strip(), email, blankToNull(input.mobile()),
                blankToNull(input.city()), blankToNull(input.country()));
        String text = new String(storage.get(upload.getTextKey()), StandardCharsets.UTF_8);
        String hidden = "application/pdf".equals(upload.getContentType())
                ? hiddenText.hiddenText(storage.get(upload.getFileKey())).orElse(null) : null;
        addToJob(upload, job, details, upload.getTextKey(), text, hidden,
                input.source() == null ? upload.getSource() : input.source(),
                input.sourceDetail() == null ? upload.getSourceDetail() : blankToNull(input.sourceDetail()));
        return upload;
    }

    /** Step one of "Add candidate": read one CV now and return what was found, for HR to check. */
    @Transactional
    public CvUpload draft(UUID jobId, MultipartFile file, ApplicationSource source, String sourceDetail) {
        Job job = openJob(jobId);
        CvUpload upload = store(job, UUID.randomUUID(), cleanName(file.getOriginalFilename()), bytes(file), source,
                sourceDetail, CurrentUser.id());
        if (upload.getStatus() == CvUpload.Status.FAILED) {
            throw ApiException.badRequest(upload.getErrorCode(), upload.getErrorMessage());
        }
        byte[] content = storage.get(upload.getFileKey());
        CvTextExtractor.Extracted extracted;
        try {
            extracted = extractor.extract(content, upload.getFileName());
        } catch (ApiException ex) {
            deleteQuietly(upload.getFileKey());
            upload.refuse(ex.getCode(), ex.getMessage(), clock.instant());
            throw ex;
        }
        CandidateDetails details = detailsExtractor.extract(extracted.text(), upload.getFileName());
        String textKey = upload.getFileKey().replaceFirst("^intake/", "intake-text/").replaceFirst("\\.[a-z]+$", ".txt");
        storage.put(textKey, extracted.text().getBytes(StandardCharsets.UTF_8));
        upload.needsDetails(textKey, extracted.contentType(), details, "Check the details and save.", clock.instant());
        return upload;
    }

    /** HR doesn't want this CV after all (it needed details or was refused): remove the file. */
    @Transactional
    public void dismiss(UUID uploadId) {
        CvUpload upload = uploads.findById(uploadId).orElseThrow(() -> ApiException.notFound("Upload"));
        if (upload.getStatus() == CvUpload.Status.ADDED || upload.getStatus() == CvUpload.Status.READING) {
            throw ApiException.conflict("cannot_dismiss", "This CV has already been added.");
        }
        deleteQuietly(upload.getFileKey());
        deleteQuietly(upload.getTextKey());
        upload.refuse("removed", "Removed by HR.", clock.instant());
    }

    /** Used by the demo data: read and score a CV right away. */
    public CvUpload addNow(Job job, String fileName, byte[] content, UUID uploadedBy, ApplicationSource source) {
        CvUpload upload = transaction.execute(status -> {
            CvUpload created = store(job, UUID.randomUUID(), fileName, content, source, null, uploadedBy);
            if (created.getStatus() == CvUpload.Status.QUEUED) {
                created.startReading(clock.instant());
            }
            return created;
        });
        if (upload.getStatus() == CvUpload.Status.READING) {
            process(upload.getId());
        }
        return uploads.findById(upload.getId()).orElseThrow();
    }

    // ------------------------------------------------------------------ helpers

    private Job openJob(UUID jobId) {
        Job job = tenancy.job(jobId);
        if (!job.getStatus().acceptsCvs()) {
            throw ApiException.conflict("job_not_open", job.getStatus().name().equals("DRAFT")
                    ? "Publish the job before adding CVs." : "This job is closed, so CVs can't be added.");
        }
        return job;
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("Could not remove {}", key, ex);
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("unreadable_upload", "The file didn't arrive in one piece. Try again.");
        }
    }

    static String cleanName(String original) {
        if (original == null || original.isBlank()) {
            return "cv";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String nameFromEmail(String email) {
        String local = email.substring(0, email.indexOf('@')).replaceAll("[._\\d-]+", " ").strip();
        return local.isEmpty() ? "Candidate" : local;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
