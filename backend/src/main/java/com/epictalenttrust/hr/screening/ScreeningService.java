package com.epictalenttrust.hr.screening;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.activity.ProcessingLog;
import com.epictalenttrust.hr.application.ApplicationKeyFacts;
import com.epictalenttrust.hr.application.ApplicationKeyFactsRepository;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.cv.CandidateDetails;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobCriterionRepository;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.settings.CompanySettings;
import com.epictalenttrust.hr.settings.CompanySettingsRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Scores one CV for one job and stores everything HR sees: the score and how it adds up, the CV line behind each
 * criterion, red flags and points to check, and the key facts read from the CV.
 */
@Service
public class ScreeningService {

    /** The CV as read: full text, the contact details found in it, and any hidden text in a PDF. */
    public record CvText(String fullText, CandidateDetails details, String hiddenTextSample) {
    }

    private final ScreeningEngine engine;
    private final CvRedactor redactor;
    private final SkillDictionary dictionary;
    private final KeyFactsExtractor keyFactsExtractor;
    private final RedFlagDetector flagDetector;
    private final AiScreeningRepository screenings;
    private final MatchEvidenceRepository evidence;
    private final ScreeningFlagRepository flags;
    private final JobCriterionRepository criteria;
    private final ApplicationKeyFactsRepository keyFacts;
    private final CompanySettingsRepository settings;
    private final JdbcClient jdbc;
    private final ProcessingLog processingLog;
    private final ActivityService activity;
    private final Clock clock;
    private final com.epictalenttrust.hr.candidate.CandidateLocationService locations;

    public ScreeningService(ScreeningEngine engine, CvRedactor redactor, SkillDictionary dictionary,
                            KeyFactsExtractor keyFactsExtractor, RedFlagDetector flagDetector,
                            AiScreeningRepository screenings, MatchEvidenceRepository evidence,
                            ScreeningFlagRepository flags, JobCriterionRepository criteria,
                            ApplicationKeyFactsRepository keyFacts, CompanySettingsRepository settings, JdbcClient jdbc,
                            ProcessingLog processingLog, ActivityService activity, Clock clock,
                            com.epictalenttrust.hr.candidate.CandidateLocationService locations) {
        this.locations = locations;
        this.engine = engine;
        this.redactor = redactor;
        this.dictionary = dictionary;
        this.keyFactsExtractor = keyFactsExtractor;
        this.flagDetector = flagDetector;
        this.screenings = screenings;
        this.evidence = evidence;
        this.flags = flags;
        this.criteria = criteria;
        this.keyFacts = keyFacts;
        this.settings = settings;
        this.jdbc = jdbc;
        this.processingLog = processingLog;
        this.activity = activity;
        this.clock = clock;
    }

    /**
     * AI shortlisting is off for this job: the CV is not scored, but its key facts (location, notice period, pay)
     * are still read, so routing to partner centres and the candidate's page still work.
     */
    @Transactional
    public void readFactsOnly(JobApplication application, CvText cv) {
        ApplicationKeyFacts facts = keyFacts.findByApplicationId(application.getId())
                .orElseGet(() -> new ApplicationKeyFacts(application.getId()));
        facts.fillFromCv(keyFactsExtractor.extract(cv.fullText()), 0, 0);
        keyFacts.save(facts);
        keyFacts.flush();
        locations.fromCv(application.getCandidateId(), application.getId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AiScreening screen(JobApplication application, Job job, CvText cv) {
        Instant started = clock.instant();
        YearMonth now = YearMonth.now(clock);
        CompanySettings clientSettings = settings.findByCompanyId(job.getCompanyId())
                .orElseGet(() -> new CompanySettings(job.getCompanyId()));
        List<ScreeningCriterion> jobCriteria = criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(job.getId())
                .stream()
                .map(c -> new ScreeningCriterion(c.getId(), c.getKind(), c.getValue(), c.getMinYears()))
                .toList();
        String redacted = redactor.redact(cv.fullText(), cv.details());
        ScreeningOutcome outcome = engine.screen(new ScreeningInput(redacted, jobCriteria, job.getMinExperienceYrs(),
                clientSettings.getShortlistCutoff(), clientSettings.getReserveCutoff(),
                dictionary.vocabulary(job.getCompanyId()), now));

        AiScreening screening = screenings.save(new AiScreening(application.getId(), engine.modelVersion(), outcome,
                job.getCriteriaVersion(), clientSettings.getShortlistCutoff(), clientSettings.getReserveCutoff()));
        for (ScreeningOutcome.CriterionResult result : outcome.evidence()) {
            evidence.save(new MatchEvidence(screening.getId(), result));
        }

        ApplicationKeyFacts facts = keyFacts.findByApplicationId(application.getId())
                .orElseGet(() -> new ApplicationKeyFacts(application.getId()));
        facts.fillFromCv(keyFactsExtractor.extract(cv.fullText()), outcome.totalYears(), outcome.relevantYears());
        keyFacts.save(facts);
        keyFacts.flush();
        locations.fromCv(application.getCandidateId(), application.getId());

        String hash = fingerprint(redacted);
        application.setCvTextHash(hash);
        List<FlagFinding> findings = new ArrayList<>(flagDetector.detect(outcome, cv.details().email() != null,
                cv.details().mobile() != null, facts.toKeyFacts(),
                new RedFlagDetector.JobLimits(job.getMaxNoticeDays(), job.getMaxCtcLpa(), job.getAcceptedLocations()),
                new RedFlagDetector.Thresholds(clientSettings.getGapFlagMonths(), clientSettings.getShortJobMonths()), now));
        if (cv.hiddenTextSample() != null) {
            findings.add(FlagFinding.red("hidden_text",
                    "Hidden text in the PDF (invisible, tiny or off the page), which people can't see but screening reads.",
                    cv.hiddenTextSample()));
        }
        if (redacted.length() >= 400) {
            copiedCv(job.getCompanyId(), application, hash).ifPresent(findings::add);
        }
        samePhone(job.getCompanyId(), application, cv.details().mobile()).ifPresent(findings::add);
        for (FlagFinding finding : findings) {
            flags.save(new ScreeningFlag(screening.getId(), application.getId(), finding));
        }

        screenings.flush();
        processingLog.aiRun(ProcessingLog.AiTask.CV_SCREENING, application.getId(), screening.getId(),
                engine.modelVersion(), true, null, started, clock.instant());
        activity.recordAs(ActorContext.Actor.SYSTEM, "screening.completed", "application", application.getId());
        return screening;
    }

    private Optional<FlagFinding> copiedCv(UUID companyId, JobApplication application, String hash) {
        return jdbc.sql("""
                        select c.full_name, c.candidate_ref from applications a
                          join jobs j on j.id = a.job_id join candidates c on c.id = a.candidate_id
                         where j.company_id = :company and a.cv_text_hash = :hash and a.candidate_id <> :candidate
                         limit 1
                        """)
                .param("company", companyId).param("hash", hash).param("candidate", application.getCandidateId())
                .query((rs, row) -> FlagFinding.red("copied_cv", "This CV's text is the same as the CV of "
                        + rs.getString("full_name") + " (" + rs.getString("candidate_ref") + "), under a different name or email.", null))
                .optional();
    }

    private Optional<FlagFinding> samePhone(UUID companyId, JobApplication application, String mobile) {
        String digits = mobile == null ? "" : mobile.replaceAll("\\D", "");
        if (digits.length() < 10) {
            return Optional.empty();
        }
        String last10 = digits.substring(digits.length() - 10);
        return jdbc.sql("""
                        select c.full_name, c.candidate_ref from candidates c
                         where c.id <> :candidate and c.mobile is not null
                           and right(regexp_replace(c.mobile, '\\D', '', 'g'), 10) = :digits
                           and exists (select 1 from applications a join jobs j on j.id = a.job_id
                                        where a.candidate_id = c.id and j.company_id = :company)
                         limit 1
                        """)
                .param("candidate", application.getCandidateId()).param("digits", last10).param("company", companyId)
                .query((rs, row) -> FlagFinding.red("shared_phone", "The same phone number is on the CV of "
                        + rs.getString("full_name") + " (" + rs.getString("candidate_ref") + ").", null))
                .optional();
    }

    static String fingerprint(String text) {
        String normalised = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
