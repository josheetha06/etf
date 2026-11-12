package com.epictalenttrust.hr.application;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.config.EttProperties;
import com.epictalenttrust.hr.cv.CandidateDetails;
import com.epictalenttrust.hr.cv.HiddenTextDetector;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.interview.Interview;
import com.epictalenttrust.hr.interview.InterviewRepository;
import com.epictalenttrust.hr.interview.InterviewStatus;
import com.epictalenttrust.hr.interview.RescheduleRequestRepository;
import com.epictalenttrust.hr.interview.RescheduleStatus;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.screening.KeyFacts;
import com.epictalenttrust.hr.screening.ScreeningService;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.storage.FileStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** What HR can do on a candidate's page besides deciding: notes, key facts, messages, details, withdrawal, other jobs. */
@Service
public class CandidateService {

    public record NoteView(UUID id, String body, String authorName, boolean mine, Instant createdAt) {
    }

    public record KeyFactsView(Integer noticePeriodDays, Boolean servingNotice, LocalDate lastWorkingDay,
                               Double currentCtcLpa, Double expectedCtcLpa, String currentLocation,
                               List<String> preferredLocations, Double totalExperienceYears,
                               Double relevantExperienceYears, Boolean willingToRelocate, String workMode,
                               String noticeSource, String ctcSource) {
    }

    public record DetailsInput(String fullName, String email, String mobile, String city, String country) {
    }

    private final Tenancy tenancy;
    private final JobApplicationRepository applications;
    private final ApplicationNoteRepository notes;
    private final ApplicationKeyFactsRepository keyFacts;
    private final CandidateRepository candidates;
    private final CompanyRepository companies;
    private final HrUserRepository users;
    private final InterviewRepository interviews;
    private final RescheduleRequestRepository requests;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final ScreeningService screening;
    private final HiddenTextDetector hiddenText;
    private final FileStorage storage;
    private final ActivityService activity;
    private final EttProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final com.epictalenttrust.hr.candidate.CandidateLocationService locations;

    public CandidateService(Tenancy tenancy, JobApplicationRepository applications, ApplicationNoteRepository notes,
                            ApplicationKeyFactsRepository keyFacts, CandidateRepository candidates,
                            CompanyRepository companies, HrUserRepository users, InterviewRepository interviews,
                            RescheduleRequestRepository requests, MessageService messages, MessageTemplates templates,
                            ScreeningService screening, HiddenTextDetector hiddenText, FileStorage storage,
                            ActivityService activity, EttProperties properties, JdbcClient jdbc, Clock clock,
                            com.epictalenttrust.hr.candidate.CandidateLocationService locations) {
        this.locations = locations;
        this.tenancy = tenancy;
        this.applications = applications;
        this.notes = notes;
        this.keyFacts = keyFacts;
        this.candidates = candidates;
        this.companies = companies;
        this.users = users;
        this.interviews = interviews;
        this.requests = requests;
        this.messages = messages;
        this.templates = templates;
        this.screening = screening;
        this.hiddenText = hiddenText;
        this.storage = storage;
        this.activity = activity;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ notes

    @Transactional(readOnly = true)
    public List<NoteView> notes(UUID applicationId) {
        tenancy.application(applicationId);
        UUID me = CurrentUser.id();
        return jdbc.sql("""
                        select n.id, n.body, u.full_name, n.author_id, n.created_at
                          from application_notes n join hr_users u on u.id = n.author_id
                         where n.application_id = :id order by n.created_at desc
                        """)
                .param("id", applicationId)
                .query((rs, row) -> new NoteView(Rows.uuid(rs, "id"), rs.getString("body"), rs.getString("full_name"),
                        me.equals(Rows.uuid(rs, "author_id")), Rows.instant(rs, "created_at")))
                .list();
    }

    @Transactional
    public List<NoteView> addNote(UUID applicationId, String body) {
        tenancy.application(applicationId);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > 4000) {
            throw ApiException.badRequest("invalid_note", "Write a note of up to 4,000 characters.");
        }
        notes.save(new ApplicationNote(applicationId, CurrentUser.id(), text));
        activity.record("note.added", "application", applicationId);
        return notes(applicationId);
    }

    @Transactional
    public List<NoteView> deleteNote(UUID applicationId, UUID noteId) {
        tenancy.application(applicationId);
        ApplicationNote note = notes.findById(noteId).filter(n -> n.getApplicationId().equals(applicationId))
                .orElseThrow(() -> ApiException.notFound("Note"));
        if (!note.getAuthorId().equals(CurrentUser.id())) {
            throw ApiException.forbidden("not_author", "Only the person who wrote a note can delete it.");
        }
        notes.delete(note);
        return notes(applicationId);
    }

    // ------------------------------------------------------------------ key facts

    @Transactional(readOnly = true)
    public KeyFactsView keyFacts(UUID applicationId) {
        tenancy.application(applicationId);
        return keyFacts.findByApplicationId(applicationId).map(CandidateService::view)
                .orElse(new KeyFactsView(null, null, null, null, null, null, List.of(), null, null, null, null, null, null));
    }

    @Transactional
    public KeyFactsView updateKeyFacts(UUID applicationId, KeyFactsView input) {
        JobApplication application = tenancy.application(applicationId);
        ApplicationKeyFacts facts = keyFacts.findByApplicationId(applicationId)
                .orElseGet(() -> new ApplicationKeyFacts(applicationId));
        String mode = input.workMode() == null || input.workMode().isBlank() ? null : input.workMode().toLowerCase(Locale.ROOT);
        if (mode != null && !List.of("office", "hybrid", "remote", "any").contains(mode)) {
            throw ApiException.badRequest("invalid_work_mode", "Work mode must be office, hybrid, remote or any.");
        }
        facts.update(new KeyFacts(input.noticePeriodDays(), input.servingNotice(), input.lastWorkingDay(),
                        input.currentCtcLpa(), input.expectedCtcLpa(), blankToNull(input.currentLocation()),
                        input.preferredLocations() == null ? List.of()
                                : input.preferredLocations().stream().map(String::strip).filter(s -> !s.isEmpty()).toList(),
                        input.willingToRelocate(), mode),
                input.totalExperienceYears(), input.relevantExperienceYears());
        keyFacts.save(facts);
        locations.fromKeyFacts(application.getCandidateId(), input.currentLocation());
        activity.record("keyfacts.updated", "application", applicationId);
        return view(facts);
    }

    private static KeyFactsView view(ApplicationKeyFacts f) {
        KeyFacts k = f.toKeyFacts();
        return new KeyFactsView(k.noticePeriodDays(), k.servingNotice(), k.lastWorkingDay(), k.currentCtcLpa(),
                k.expectedCtcLpa(), k.currentLocation(), k.preferredLocations(), f.getTotalExperienceYears(),
                f.getRelevantExperienceYears(), k.willingToRelocate(), k.workMode(), f.getNoticeSource(), f.getCtcSource());
    }

    // ------------------------------------------------------------------ candidate details

    /** Corrects details misread from the CV. Only allowed while the person is in this client's jobs alone. */
    @Transactional
    public void updateDetails(UUID applicationId, DetailsInput input) {
        JobApplication application = tenancy.application(applicationId);
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        long elsewhere = jdbc.sql("""
                        select count(*) from applications a join jobs j on j.id = a.job_id
                         where a.candidate_id = :c and j.company_id <> :company
                        """)
                .param("c", candidate.getId()).param("company", CurrentUser.companyId()).query(Long.class).single();
        if (elsewhere > 0) {
            throw ApiException.conflict("shared_candidate",
                    "These details are also used in another hiring process, so ask ETT support to change them.");
        }
        String email = input.email() == null ? "" : input.email().strip().toLowerCase(Locale.ROOT);
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw ApiException.badRequest("invalid_email", "Enter a valid email address.");
        }
        if (input.fullName() == null || input.fullName().isBlank()) {
            throw ApiException.badRequest("name_required", "Enter the candidate's name.");
        }
        candidates.findByEmail(email).filter(other -> !other.getId().equals(candidate.getId())).ifPresent(other -> {
            throw ApiException.conflict("email_in_use", "Another candidate already uses this email address.");
        });
        candidate.updateDetails(input.fullName().strip(), email, blankToNull(input.mobile()), blankToNull(input.city()),
                blankToNull(input.country()));
        activity.record("candidate.updated", "application", applicationId);
    }

    // ------------------------------------------------------------------ withdraw and offer outcome

    /** The candidate pulled out. Upcoming interviews are cancelled quietly and reminders stop. */
    @Transactional
    public void withdraw(UUID applicationId, String reason) {
        JobApplication application = tenancy.application(applicationId);
        if (!application.isActive()) {
            throw ApiException.conflict("not_active", "This candidate is no longer in the process.");
        }
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 3) {
            throw ApiException.badRequest("reason_required", "Say briefly why the candidate withdrew.");
        }
        for (Interview interview : interviews.findByApplicationId(applicationId)) {
            if (interview.getStatus() == InterviewStatus.DRAFT || interview.getStatus().isUpcoming()) {
                requests.findFirstByInterviewIdAndStatus(interview.getId(), RescheduleStatus.PENDING)
                        .ifPresent(r -> r.decline(CurrentUser.id()));
                interview.markCancelled();
            }
        }
        application.withdraw(why, clock.instant());
        activity.record("application.withdrawn", "application", applicationId);
    }

    /** After an offer: hired, or the candidate turned it down. */
    @Transactional
    public void recordOffer(UUID applicationId, boolean accepted, String note) {
        JobApplication application = tenancy.application(applicationId);
        if (application.getStage() != ApplicationStage.OFFER || !application.isActive()) {
            throw ApiException.conflict("no_offer", "This candidate hasn't been offered the job.");
        }
        if (accepted) {
            application.markHired(clock.instant());
            activity.record("offer.accepted", "application", applicationId);
        } else {
            application.withdraw(note == null || note.isBlank() ? "Turned down the offer" : note.strip(), clock.instant());
            activity.record("offer.declined", "application", applicationId);
        }
    }

    // ------------------------------------------------------------------ messages

    @Transactional(readOnly = true)
    public List<MessageTemplates.Template> templates(UUID applicationId) {
        JobApplication application = tenancy.application(applicationId);
        Job job = tenancy.job(application.getJobId());
        String first = candidates.findById(application.getCandidateId()).map(c -> firstName(c.getFullName())).orElse("there");
        String company = companies.findById(job.getCompanyId()).map(c -> c.getName()).orElse("");
        String me = users.findById(CurrentUser.id()).map(u -> u.getFullName()).orElse("The hiring team");
        return templates.hrTemplates(first, job.getTitle(), company, me);
    }

    @Transactional
    public void sendMessage(UUID applicationId, String subject, String body) {
        JobApplication application = tenancy.application(applicationId);
        String s = subject == null ? "" : subject.strip();
        String b = body == null ? "" : body.strip();
        if (s.isEmpty() || s.length() > 200) {
            throw ApiException.badRequest("invalid_subject", "Write a subject of up to 200 characters.");
        }
        if (b.length() < 10 || b.length() > 8000) {
            throw ApiException.badRequest("invalid_body", "Write a message of 10 to 8,000 characters.");
        }
        messages.emailCandidate(application, null, SenderKind.HR_USER, CurrentUser.id(), new MessageTemplates.Content(s, b));
        activity.record("message.sent", "application", applicationId);
    }

    // ------------------------------------------------------------------ another job

    /** Puts the same candidate and CV into another open job of this client, scored against that job's criteria. */
    @Transactional
    public UUID addToJob(UUID applicationId, UUID targetJobId) {
        JobApplication source = tenancy.application(applicationId);
        Job from = tenancy.job(source.getJobId());
        Job target = tenancy.job(targetJobId);
        if (!target.getStatus().acceptsCvs()) {
            throw ApiException.conflict("job_not_open", "That job isn't open for CVs.");
        }
        if (source.getCvFileKey() == null || source.getCvTextKey() == null) {
            throw ApiException.conflict("no_cv", "This candidate's CV is no longer stored, so upload it to the other job instead.");
        }
        if (applications.findByJobIdAndCandidateId(targetJobId, source.getCandidateId()).isPresent()) {
            throw ApiException.conflict("already_in_job", "This candidate is already in that job.");
        }
        Candidate candidate = candidates.findById(source.getCandidateId()).orElseThrow();
        JobApplication copy = applications.saveAndFlush(new JobApplication(targetJobId, candidate.getId(),
                source.getSource(), CurrentUser.id()));
        copy.setSourceDetail("Added from " + from.getJobRef() + " · " + from.getTitle());
        String extension = source.getCvFileKey().substring(source.getCvFileKey().lastIndexOf('.') + 1);
        String base = "%s/%s/%s".formatted(target.getCompanyId(), target.getId(), copy.getId());
        String fileKey = "cv/" + base + "." + extension;
        String textKey = "cv-text/" + base + ".txt";
        byte[] file = storage.get(source.getCvFileKey());
        byte[] text = storage.get(source.getCvTextKey());
        storage.put(fileKey, file);
        storage.put(textKey, text);
        copy.attachCv(fileKey, textKey, LocalDate.now(clock).plusDays(properties.retention().cvDays()));
        String hidden = "pdf".equals(extension) ? hiddenText.hiddenText(file).orElse(null) : null;
        screening.screen(copy, target, new ScreeningService.CvText(new String(text, StandardCharsets.UTF_8),
                new CandidateDetails(candidate.getFullName(), candidate.getEmail(), candidate.getMobile(),
                        candidate.getCity(), candidate.getCountry()), hidden));
        activity.record("application.copied", "application", copy.getId());
        return copy.getId();
    }

    static String firstName(String fullName) {
        return fullName == null || fullName.isBlank() ? "there" : fullName.strip().split("\\s+")[0];
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
