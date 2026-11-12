package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationDtos.ApplicationRow;
import com.epictalenttrust.hr.application.ApplicationQueries;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.ApplicationStatus;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.interview.InterviewDtos.PlanItem;
import com.epictalenttrust.hr.interview.InterviewDtos.Round1Plan;
import com.epictalenttrust.hr.interview.InterviewDtos.Round1Request;
import com.epictalenttrust.hr.interview.InterviewDtos.SaveResult;
import com.epictalenttrust.hr.interview.InterviewDtos.TimeInput;
import com.epictalenttrust.hr.job.InterviewMode;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRound;
import com.epictalenttrust.hr.job.JobRoundRepository;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.security.ActorContext;
import com.epictalenttrust.hr.security.CurrentUser;
import com.epictalenttrust.hr.shortlist.ShortlistDecisionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI interview rounds, several candidates at once: HR sets one time per candidate ready for the round. The AI agent
 * suggests free times and sends the invitations.
 */
@Service
public class SchedulingService {

    private final Tenancy tenancy;
    private final ApplicationQueries applicationQueries;
    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final JobRoundRepository rounds;
    private final CompanyRepository companies;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final InterviewFacts facts;
    private final ActivityService activity;
    private final com.epictalenttrust.hr.candidateportal.CandidateLinks candidateLinks;
    private final Clock clock;

    public SchedulingService(Tenancy tenancy, ApplicationQueries applicationQueries, JobApplicationRepository applications,
                             InterviewRepository interviews, JobRoundRepository rounds, CompanyRepository companies,
                             MessageService messages, MessageTemplates templates, InterviewFacts facts,
                             ActivityService activity, com.epictalenttrust.hr.candidateportal.CandidateLinks candidateLinks,
                             Clock clock) {
        this.candidateLinks = candidateLinks;
        this.tenancy = tenancy;
        this.applicationQueries = applicationQueries;
        this.applications = applications;
        this.interviews = interviews;
        this.rounds = rounds;
        this.companies = companies;
        this.messages = messages;
        this.templates = templates;
        this.facts = facts;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Round1Plan plan(UUID jobId, Integer roundParam, String hrZoneParam) {
        Job job = tenancy.job(jobId);
        ZoneId hrZone = hrZone(job, hrZoneParam);
        int round = roundParam == null ? firstAiRound(jobId) : roundParam;
        JobRound plan = roundPlan(jobId, round);
        Duration length = Duration.ofMinutes(plan.getDurationMin());
        Instant now = clock.instant();

        List<ApplicationRow> eligible = eligible(job, round);
        List<SlotSuggester.Interval> busy = interviews.findBusy(CurrentUser.id(), now, now.plus(Duration.ofDays(30))).stream()
                .filter(i -> i.getStartAt() != null)
                .map(i -> new SlotSuggester.Interval(i.getStartAt(), i.getStartAt().plus(Duration.ofMinutes(
                        i.getDurationMin() == null ? 45 : i.getDurationMin()))))
                .collect(Collectors.toCollection(ArrayList::new));

        Map<UUID, Interview> existing = eligible.stream()
                .map(row -> current(row.id(), round))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Interview::getApplicationId, Function.identity()));

        List<SlotSuggester.CandidateZone> needingTimes = eligible.stream()
                .filter(row -> !existing.containsKey(row.id()))
                .map(row -> new SlotSuggester.CandidateZone(row.id(), TimeZones.forCountry(row.country())))
                .toList();
        Map<UUID, Instant> suggestions = SlotSuggester.suggest(needingTimes, hrZone, now, length, busy);

        List<PlanItem> items = new ArrayList<>();
        for (ApplicationRow row : eligible) {
            Interview interview = existing.get(row.id());
            ZoneId candidateZone = interview != null
                    ? TimeZones.parseOr(interview.getCandidateTz(), TimeZones.forCountry(row.country()))
                    : TimeZones.forCountry(row.country());
            items.add(new PlanItem(row.id(), row.candidateName(), row.city(), row.country(), candidateZone.getId(),
                    row.matchScore(),
                    interview == null ? null : interview.getId(),
                    interview == null ? null : interview.getStatus(),
                    interview != null ? interview.getStartAt() : suggestions.get(row.id()),
                    interview == null ? null : interview.getReplyBy(),
                    interview == null && suggestions.containsKey(row.id())));
        }
        // Only times still being set count: sent or finished interviews keep their own deadlines.
        Instant earliest = items.stream()
                .filter(item -> item.status() == null || item.status() == InterviewStatus.DRAFT)
                .map(PlanItem::startAt)
                .filter(java.util.Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        return new Round1Plan(job.getId(), job.getTitle(), job.getJobRef(), round, plan.getName(), plan.getDurationMin(),
                plan.getMode() == null ? InterviewMode.ONLINE : plan.getMode(), hrZone.getId(),
                defaultReplyBy(now, hrZone, earliest), items);
    }

    @Transactional
    public SaveResult save(UUID jobId, Integer roundParam, Round1Request request, boolean send) {
        Job job = tenancy.job(jobId);
        RoundSchedulingService.requireLive(job);
        int round = roundParam == null ? firstAiRound(jobId) : roundParam;
        JobRound plan = roundPlan(jobId, round);
        Instant now = clock.instant();
        if (!request.replyBy().isAfter(now)) {
            throw ApiException.badRequest("reply_by_past", "The reply deadline must be in the future.");
        }
        Map<UUID, ApplicationRow> eligible = eligible(job, round).stream()
                .collect(Collectors.toMap(ApplicationRow::id, Function.identity()));
        UUID hrUserId = CurrentUser.id();
        int saved = 0;
        int sent = 0;
        for (TimeInput item : request.items()) {
            if (!eligible.containsKey(item.applicationId())) {
                throw ApiException.badRequest("not_ready", "Only candidates who have reached " + plan.getName()
                        + " can be invited.");
            }
            if (!item.startAt().isAfter(now.plus(Duration.ofHours(1)))) {
                throw ApiException.badRequest("time_past",
                        "Interview times must be at least an hour from now (" + eligible.get(item.applicationId()).candidateName() + ").");
            }
            ZoneId candidateZone = TimeZones.parseOr(item.candidateZone(), TimeZones.INDIA);
            Interview interview = current(item.applicationId(), round).orElse(null);
            if (interview != null && interview.getStatus() != InterviewStatus.DRAFT) {
                continue;
            }
            if (interview == null) {
                interview = new Interview(item.applicationId(), round, InterviewMode.ONLINE,
                        plan.getDurationMin());
            }
            Instant replyBy = request.replyBy().isBefore(item.startAt().minus(Duration.ofHours(2)))
                    ? request.replyBy() : item.startAt().minus(Duration.ofHours(2));
            interview.setTime(item.startAt(), candidateZone.getId(), replyBy, hrUserId);
            interview = interviews.save(interview);
            saved++;
            if (send) {
                invite(interview);
                sent++;
            }
        }
        if (sent > 0) {
            activity.record("ai_interview.invitations_sent", "job", job.getId());
        }
        return new SaveResult(saved, sent);
    }

    private void invite(Interview interview) {
        JobApplication application = applications.findById(interview.getApplicationId()).orElseThrow();
        interview.markInvited(clock.instant());
        if (application.getStage() == ApplicationStage.SHORTLIST && interview.getRound() == 1) {
            application.moveTo(ApplicationStage.ROUND1);
        }
        interviews.flush();
        messages.emailCandidate(application, interview.getId(), SenderKind.SYSTEM, null,
                templates.invitation(facts.of(interview, application)
                        .withCandidatePage(candidateLinks.issue(application.getId(), interview))));
        activity.recordAs(ActorContext.Actor.SYSTEM, "interview.invited", "interview", interview.getId());
    }

    /** Candidates who have reached this round and still need (or have) an AI interview time. */
    private List<ApplicationRow> eligible(Job job, int round) {
        return applicationQueries.forJob(job.getId()).stream()
                .filter(row -> row.status() == ApplicationStatus.ACTIVE)
                .filter(row -> round == 1
                        ? (row.stage() == ApplicationStage.ROUND1
                           || (row.stage() == ApplicationStage.SHORTLIST && row.decision() == ShortlistDecisionType.SHORTLISTED))
                        : row.stage() == ApplicationStage.forRound(round))
                .toList();
    }

    private Optional<Interview> current(UUID applicationId, int round) {
        return interviews.findFirstByApplicationIdAndRoundAndStatusNotOrderByCreatedAtDesc(applicationId, round,
                InterviewStatus.CANCELLED);
    }

    private JobRound roundPlan(UUID jobId, int round) {
        JobRound plan = rounds.findByJobIdAndRound(jobId, round)
                .orElseThrow(() -> ApiException.notFound("Round " + round));
        if (plan.getKind() != com.epictalenttrust.hr.job.RoundKind.AI_INTERVIEW) {
            throw ApiException.badRequest("not_ai_round", plan.getName() + " isn't an AI interview round.");
        }
        return plan;
    }

    /** The job's first AI interview round. */
    private int firstAiRound(UUID jobId) {
        return rounds.findByJobIdOrderByRound(jobId).stream()
                .filter(r -> r.getKind() == com.epictalenttrust.hr.job.RoundKind.AI_INTERVIEW)
                .map(JobRound::getRound).findFirst()
                .orElseThrow(() -> ApiException.badRequest("no_ai_round", "This job has no AI interview round."));
    }

    private ZoneId hrZone(Job job, String param) {
        String country = companies.findById(job.getCompanyId()).map(c -> c.getCountry()).orElse(null);
        return TimeZones.parseOr(param, TimeZones.forCountry(country));
    }

    static Instant defaultReplyBy(Instant now, ZoneId hrZone, Instant earliestStart) {
        Instant candidate = now.atZone(hrZone).toLocalDate().plusDays(2).atTime(LocalTime.of(18, 0)).atZone(hrZone).toInstant();
        if (earliestStart != null && candidate.isAfter(earliestStart.minus(Duration.ofHours(12)))) {
            Instant earlier = earliestStart.minus(Duration.ofHours(12));
            return earlier.isAfter(now.plus(Duration.ofHours(2))) ? earlier : now.plus(Duration.ofHours(2));
        }
        return candidate;
    }
}
