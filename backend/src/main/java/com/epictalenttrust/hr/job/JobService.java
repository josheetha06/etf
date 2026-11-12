package com.epictalenttrust.hr.job;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.ApplicationStage;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.common.ApiException;
import com.epictalenttrust.hr.common.ChangeLog;
import com.epictalenttrust.hr.common.Rows;
import com.epictalenttrust.hr.common.Tenancy;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.hruser.HrUserRepository;
import com.epictalenttrust.hr.job.JobDtos.Changes;
import com.epictalenttrust.hr.job.JobDtos.Counts;
import com.epictalenttrust.hr.job.JobDtos.CriteriaCheck;
import com.epictalenttrust.hr.job.JobDtos.CriterionInput;
import com.epictalenttrust.hr.job.JobDtos.CriterionView;
import com.epictalenttrust.hr.job.JobDtos.JobDetail;
import com.epictalenttrust.hr.job.JobDtos.JobRequest;
import com.epictalenttrust.hr.job.JobDtos.JobSummary;
import com.epictalenttrust.hr.job.JobDtos.PersonRef;
import com.epictalenttrust.hr.job.JobDtos.Pipeline;
import com.epictalenttrust.hr.job.JobDtos.RoundCount;
import com.epictalenttrust.hr.job.JobDtos.RoundInput;
import com.epictalenttrust.hr.job.JobDtos.RoundView;
import com.epictalenttrust.hr.job.JobDtos.StatusChange;
import com.epictalenttrust.hr.screening.SkillDictionary;
import com.epictalenttrust.hr.security.Access;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Jobs: details, skills, the interview plan (each job sets its own rounds), AI shortlisting on or off, the job
 * description, who owns it and who the hiring manager is, and its status (open, on hold, closed).
 */
@Service
public class JobService {

    /** Published after a criteria change is saved, so CVs already in the job are scored again. */
    public record CriteriaChanged(UUID jobId, UUID changedBy) {
    }

    /** Published when AI shortlisting is turned off: candidates waiting in screening move straight to Round 1. */
    public record ShortlistingTurnedOff(UUID jobId) {
    }

    private final JobRepository jobs;
    private final JobCriterionRepository criteria;
    private final JobRoundRepository rounds;
    private final JobApplicationRepository applications;
    private final CompanyRepository companies;
    private final HrUserRepository users;
    private final Tenancy tenancy;
    private final Access access;
    private final ChangeLog changeLog;
    private final ActivityService activity;
    private final CriteriaAdvisor advisor;
    private final SkillDictionary dictionary;
    private final ApplicationEventPublisher events;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final com.epictalenttrust.hr.interview.JobCloseOut closeOut;

    public JobService(JobRepository jobs, JobCriterionRepository criteria, JobRoundRepository rounds,
                      JobApplicationRepository applications, CompanyRepository companies, HrUserRepository users,
                      Tenancy tenancy, Access access, ChangeLog changeLog, ActivityService activity,
                      CriteriaAdvisor advisor, SkillDictionary dictionary, ApplicationEventPublisher events,
                      JdbcClient jdbc, Clock clock, com.epictalenttrust.hr.interview.JobCloseOut closeOut) {
        this.closeOut = closeOut;
        this.jobs = jobs;
        this.criteria = criteria;
        this.rounds = rounds;
        this.applications = applications;
        this.companies = companies;
        this.users = users;
        this.tenancy = tenancy;
        this.access = access;
        this.changeLog = changeLog;
        this.activity = activity;
        this.advisor = advisor;
        this.dictionary = dictionary;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Jobs the signed-in person can see, optionally searched and filtered by status (open, on_hold, closed, draft) or
     * by their own stars (starred). Starred jobs come first.
     */
    @Transactional(readOnly = true)
    public List<JobSummary> list(String query, String statusFilter) {
        UUID companyId = CurrentUser.companyId();
        Map<UUID, Counts> counts = countsFor(companyId, null);
        Map<UUID, List<RoundCount>> roundCounts = roundCounts(null);
        String pattern = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT) + "%";
        String status = statusFilter == null ? "all" : statusFilter.toLowerCase(Locale.ROOT);
        record Row(UUID id, String ref, String title, String location, ContractType contract, Integer openings,
                   JobStatus status, Instant published, Instant created, boolean ai, String reason, UUID ownerId,
                   String ownerName, UUID managerId, String managerName, boolean starred) {
        }
        List<Row> rows = access.bind(jdbc.sql("""
                        select j.id, j.job_ref, j.title, j.location, j.contract_type, j.openings, j.status, j.published_at,
                               j.created_at, j.ai_shortlisting, j.status_reason, j.owner_id, o.full_name as owner_name,
                               j.hiring_manager_id, m.full_name as manager_name,
                               exists (select 1 from job_stars st where st.job_id = j.id and st.hr_user_id = :me) as starred
                          from jobs j
                          left join hr_users o on o.id = j.owner_id
                          left join hr_users m on m.id = j.hiring_manager_id
                         where j.company_id = :companyId and j.status <> 'archived'
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                           and (cast(:pattern as text) is null or lower(j.title) like :pattern
                                or lower(j.job_ref) like :pattern or lower(coalesce(j.location, '')) like :pattern)
                           and (:status = 'all'
                                or (:status = 'starred' and exists (select 1 from job_stars st where st.job_id = j.id
                                                                       and st.hr_user_id = :me))
                                or (:status = 'open' and j.status in ('open', 'shortlist_confirmed'))
                                or (:status = 'on_hold' and j.status = 'on_hold')
                                or (:status = 'draft' and j.status = 'draft')
                                or (:status = 'closed' and j.status = 'closed'))
                         order by exists (select 1 from job_stars st where st.job_id = j.id and st.hr_user_id = :me) desc,
                                  case j.status when 'open' then 0 when 'shortlist_confirmed' then 0 when 'on_hold' then 1
                                  when 'draft' then 2 else 3 end, j.created_at desc
                        """))
                .param("pattern", pattern)
                .param("status", status)
                .param("me", CurrentUser.id())
                .query((rs, n) -> new Row(Rows.uuid(rs, "id"), rs.getString("job_ref"), rs.getString("title"),
                        rs.getString("location"), Rows.enumOf(rs, "contract_type", ContractType.class),
                        Rows.integer(rs, "openings"), Rows.enumOf(rs, "status", JobStatus.class),
                        Rows.instant(rs, "published_at"), Rows.instant(rs, "created_at"),
                        rs.getBoolean("ai_shortlisting"), rs.getString("status_reason"), Rows.uuid(rs, "owner_id"),
                        rs.getString("owner_name"), Rows.uuid(rs, "hiring_manager_id"), rs.getString("manager_name"),
                        rs.getBoolean("starred")))
                .list();
        Map<UUID, Changes> changes = changeLog.forRecords("jobs", rows.stream().map(Row::id).toList());
        return rows.stream().map(r -> new JobSummary(r.id(), r.ref(), r.title(), r.location(), r.contract(),
                        r.openings(), r.status(), r.published(), r.created(),
                        counts.getOrDefault(r.id(), new Counts(0, 0, 0, 0, 0)), r.ai(), r.reason(),
                        person(r.ownerId(), r.ownerName()), person(r.managerId(), r.managerName()),
                        changes.getOrDefault(r.id(), new Changes(null, r.created(), null, r.created())),
                        roundCounts.getOrDefault(r.id(), List.of()), r.starred()))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetail detail(UUID jobId) {
        return toDetail(tenancy.job(jobId));
    }

    /** Live advice on criteria while HR types. */
    @Transactional(readOnly = true)
    public CriteriaCheck check(List<CriterionInput> input) {
        List<CriteriaAdvisor.Item> items = advisor.check(CurrentUser.companyId(), input == null ? List.of() : input);
        return new CriteriaCheck(items, items.stream().anyMatch(CriteriaAdvisor.Item::blocking));
    }

    /** The company's default round plan, which pre-fills a new job. */
    @Transactional(readOnly = true)
    public List<RoundView> defaultPlan() {
        return jdbc.sql("""
                        select round, name, kind, mode, duration_min, target_days from round_templates
                         where company_id = :c order by round
                        """)
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> new RoundView(rs.getInt("round"), rs.getString("name"),
                        Rows.enumOf(rs, "kind", RoundKind.class), Rows.enumOf(rs, "mode", InterviewMode.class),
                        Rows.integer(rs, "duration_min"), Rows.integer(rs, "target_days")))
                .list();
    }

    @Transactional
    public JobDetail create(JobRequest request) {
        UUID companyId = CurrentUser.companyId();
        Job job = new Job(nextJobRef(companyId), companyId);
        job.describe(request.title().strip(), blankToNull(request.location()), request.contractType(),
                request.openings(), request.minExperienceYrs());
        job.setLimits(request.maxNoticeDays(), request.maxCtcLpa(), tidyList(request.acceptedLocations()));
        job.setAiShortlisting(request.aiShortlisting() == null || request.aiShortlisting());
        job.setDescription(blankToNull(request.description()));
        job.attachDescriptionFile(null, blankToNull(request.descriptionFileName()));
        job.setOwnerId(owner(request.ownerId(), CurrentUser.id()));
        job.setHiringManagerId(hiringManager(request.hiringManagerId()));
        jobs.saveAndFlush(job);
        saveCriteria(job, request.criteria(), 1);
        List<RoundInput> plan = request.rounds() == null || request.rounds().isEmpty()
                ? defaultPlan().stream().map(r -> new RoundInput(r.name(), r.kind(), r.mode(), r.durationMin(), r.targetDays()))
                        .toList()
                : request.rounds();
        replaceRounds(job.getId(), plan);
        activity.record("job.created", "job", job.getId());
        return toDetail(job);
    }

    @Transactional
    public JobDetail update(UUID jobId, JobRequest request) {
        Job job = tenancy.job(jobId);
        if (job.getStatus() == JobStatus.CLOSED || job.getStatus() == JobStatus.ARCHIVED) {
            throw ApiException.conflict("job_closed", "This job is closed. Open it again to make changes.");
        }
        job.describe(request.title().strip(), blankToNull(request.location()), request.contractType(),
                request.openings(), request.minExperienceYrs());
        job.setLimits(request.maxNoticeDays(), request.maxCtcLpa(), tidyList(request.acceptedLocations()));
        if (request.description() != null) {
            job.setDescription(blankToNull(request.description()));
            job.attachDescriptionFile(null, blankToNull(request.descriptionFileName()));
        }
        if (request.ownerId() != null && !request.ownerId().equals(job.getOwnerId())) {
            if (!CurrentUser.get().role().seesAllJobs()) {
                throw ApiException.forbidden("admin_only", "Only HR admins can hand a job to someone else.");
            }
            job.setOwnerId(owner(request.ownerId(), job.getOwnerId()));
        }
        job.setHiringManagerId(hiringManager(request.hiringManagerId()));
        long cvs = applications.countByJobId(jobId);
        if (request.criteria() != null && !sameCriteria(job, request.criteria())) {
            if (cvs == 0) {
                criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(jobId).forEach(criteria::delete);
                criteria.flush();
                saveCriteria(job, request.criteria(), job.getCriteriaVersion());
            } else if (!Boolean.TRUE.equals(request.rescore())) {
                throw ApiException.conflict("rescore_needed", "Changing the criteria re-scores all " + cvs
                        + " CVs in this job. Earlier scores are kept in each candidate's history.");
            } else {
                criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(jobId).forEach(c -> c.retire(clock.instant()));
                saveCriteria(job, request.criteria(), job.nextCriteriaVersion());
                if (job.isAiShortlisting()) {
                    events.publishEvent(new CriteriaChanged(jobId, CurrentUser.id()));
                }
                activity.record("job.criteria_changed", "job", jobId);
            }
        }
        if (request.rounds() != null && !request.rounds().isEmpty()) {
            replaceRounds(jobId, request.rounds());
        }
        boolean turnOff = request.aiShortlisting() != null && !request.aiShortlisting() && job.isAiShortlisting();
        if (request.aiShortlisting() != null) {
            job.setAiShortlisting(request.aiShortlisting());
        }
        if (turnOff) {
            startRoundsForWaitingCandidates(job);
        }
        activity.record("job.updated", "job", jobId);
        return toDetail(job);
    }

    @Transactional
    public JobDetail publish(UUID jobId) {
        Job job = tenancy.job(jobId);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw ApiException.conflict("not_draft", "Only draft jobs can be published.");
        }
        List<JobCriterion> jobCriteria = criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(jobId);
        if (jobCriteria.stream().noneMatch(c -> c.getKind() == CriterionKind.MUST_HAVE)) {
            throw ApiException.badRequest("no_must_have",
                    "Add at least one must-have skill before publishing. Panels score candidates on the job's skills.");
        }
        if (rounds.findByJobIdOrderByRound(jobId).isEmpty()) {
            throw ApiException.badRequest("no_rounds", "Add at least one interview round before publishing.");
        }
        job.publish(clock.instant());
        activity.record("job.published", "job", jobId);
        return toDetail(job);
    }

    @Transactional
    public JobDetail close(UUID jobId) {
        return changeStatus(jobId, new StatusChange(JobStatus.CLOSED, null));
    }

    /**
     * Puts a job on hold (no new CVs or bookings; booked interviews go ahead), closes it (read-only) or opens it
     * again. Holding and closing need a reason, shown on the job.
     */
    @Transactional
    public JobDetail changeStatus(UUID jobId, StatusChange change) {
        Job job = tenancy.job(jobId);
        JobStatus current = job.getStatus();
        String reason = change.reason() == null ? null : change.reason().strip();
        Instant now = clock.instant();
        switch (change.status()) {
            case ON_HOLD -> {
                if (!current.isLive()) {
                    throw ApiException.conflict("not_open", "Only open jobs can be put on hold.");
                }
                requireReason(reason, "putting the job on hold");
                job.changeStatus(JobStatus.ON_HOLD, reason, CurrentUser.id(), now);
                activity.record("job.on_hold", "job", jobId);
            }
            case CLOSED -> {
                if (current == JobStatus.CLOSED || current == JobStatus.ARCHIVED) {
                    throw ApiException.conflict("already_closed", "This job is already closed.");
                }
                if (current != JobStatus.DRAFT) {
                    requireReason(reason, "closing the job");
                }
                job.changeStatus(JobStatus.CLOSED, reason, CurrentUser.id(), now);
                activity.record("job.closed", "job", jobId);
                if (current != JobStatus.DRAFT) {
                    closeOut.closeOut(job, reason);
                }
            }
            case OPEN, SHORTLIST_CONFIRMED -> {
                if (current != JobStatus.ON_HOLD && current != JobStatus.CLOSED) {
                    throw ApiException.conflict("not_paused", "Only jobs on hold or closed can be opened again.");
                }
                if (job.getPublishedAt() == null) {
                    job.changeStatus(JobStatus.DRAFT, null, CurrentUser.id(), now);
                } else {
                    job.changeStatus(job.getShortlistConfirmedAt() != null ? JobStatus.SHORTLIST_CONFIRMED : JobStatus.OPEN,
                            null, CurrentUser.id(), now);
                }
                activity.record("job.reopened", "job", jobId);
            }
            default -> throw ApiException.badRequest("invalid_status", "Choose open, on hold or closed.");
        }
        return toDetail(job);
    }

    /** Stars or un-stars a job for the signed-in person only. Starred jobs come first and have their own tab. */
    @Transactional
    public boolean star(UUID jobId, boolean on) {
        tenancy.job(jobId);
        if (on) {
            jdbc.sql("insert into job_stars (hr_user_id, job_id) values (:u, :j) on conflict do nothing")
                    .param("u", CurrentUser.id()).param("j", jobId).update();
        } else {
            jdbc.sql("delete from job_stars where hr_user_id = :u and job_id = :j")
                    .param("u", CurrentUser.id()).param("j", jobId).update();
        }
        return on;
    }

    private boolean starred(UUID jobId) {
        return jdbc.sql("select exists (select 1 from job_stars where hr_user_id = :u and job_id = :j)")
                .param("u", CurrentUser.id()).param("j", jobId).query(Boolean.class).single();
    }

    /** What closing the job would do: interviews cancelled and candidates told. */
    @Transactional(readOnly = true)
    public com.epictalenttrust.hr.interview.JobCloseOut.Impact closeImpact(UUID jobId) {
        tenancy.job(jobId);
        return closeOut.impact(jobId);
    }

    /** People who can own a job or be named as its hiring manager. */
    @Transactional(readOnly = true)
    public List<PersonOption> people() {
        return jdbc.sql("""
                        select id, full_name, role from hr_users
                         where company_id = :c and status <> 'disabled' and role in ('admin', 'recruiter', 'hiring_manager')
                         order by case role when 'hiring_manager' then 0 else 1 end, full_name
                        """)
                .param("c", CurrentUser.companyId())
                .query((rs, n) -> new PersonOption(Rows.uuid(rs, "id"), rs.getString("full_name"), rs.getString("role")))
                .list();
    }

    public record PersonOption(UUID id, String fullName, String role) {
    }

    // ---------------------------------------------------------------- rounds

    /**
     * Saves the plan in order. Rounds some candidate has already reached keep their position and type (their name,
     * length and target time can still change); later rounds can be changed, reordered, added or removed.
     */
    private void replaceRounds(UUID jobId, List<RoundInput> input) {
        if (input.size() > ApplicationStage.MAX_ROUNDS) {
            throw ApiException.badRequest("too_many_rounds", "A job can have up to " + ApplicationStage.MAX_ROUNDS + " rounds.");
        }
        List<JobRound> existing = rounds.findByJobIdOrderByRound(jobId);
        int locked = lockedUpTo(jobId);
        if (input.size() < locked) {
            throw ApiException.conflict("round_in_use", "Candidates have already reached Round " + locked
                    + ", so the plan needs at least " + locked + " rounds.");
        }
        for (int i = 0; i < locked && i < existing.size(); i++) {
            if (existing.get(i).getKind() != input.get(i).kind()) {
                throw ApiException.conflict("round_in_use", "Round " + (i + 1) + " (" + existing.get(i).getName()
                        + ") already has candidates, so its type can't change. You can still rename it or change its length.");
            }
        }
        for (JobRound round : existing) {
            if (round.getRound() > locked) {
                rounds.delete(round);
            }
        }
        rounds.flush();
        for (int i = 0; i < input.size(); i++) {
            RoundInput item = input.get(i);
            String name = item.name().strip();
            InterviewMode mode = item.mode() == null ? InterviewMode.ONLINE : item.mode();
            if (i < locked && i < existing.size()) {
                existing.get(i).update(name, item.kind(), mode, item.durationMin(), item.targetDays());
            } else {
                rounds.save(new JobRound(jobId, i + 1, name, item.kind(), mode, item.durationMin(), item.targetDays()));
            }
        }
    }

    /** The highest round any candidate of this job has reached or been booked for (0 if none). */
    private int lockedUpTo(UUID jobId) {
        Integer reached = jdbc.sql("""
                        select greatest(
                                 coalesce((select max(case when a.stage = 'offer' then (select count(*) from job_rounds where job_id = :j)
                                                           when a.stage::text like 'round_' then substr(a.stage::text, 6)::int
                                                           else 0 end)
                                             from applications a where a.job_id = :j), 0),
                                 coalesce((select max(i.round) from interviews i join applications a on a.id = i.application_id
                                            where a.job_id = :j), 0))
                        """)
                .param("j", jobId).query(Integer.class).single();
        return reached == null ? 0 : reached;
    }

    /** AI shortlisting was turned off: anyone still waiting in screening goes straight to Round 1. */
    private void startRoundsForWaitingCandidates(Job job) {
        List<JobApplication> waiting = applications.findByJobId(job.getId()).stream()
                .filter(a -> a.isActive() && a.getStage() == ApplicationStage.SCREENING)
                .toList();
        for (JobApplication application : waiting) {
            application.moveTo(ApplicationStage.ROUND1);
        }
        events.publishEvent(new ShortlistingTurnedOff(job.getId()));
    }

    // ---------------------------------------------------------------- helpers

    private UUID owner(UUID requested, UUID fallback) {
        if (requested == null) {
            return fallback;
        }
        boolean ok = jdbc.sql("""
                        select exists (select 1 from hr_users where id = :u and company_id = :c and status <> 'disabled'
                                          and role in ('admin', 'recruiter'))
                        """)
                .param("u", requested).param("c", CurrentUser.companyId()).query(Boolean.class).single();
        if (!ok) {
            throw ApiException.badRequest("invalid_owner", "Choose an HR admin or recruiter from your team.");
        }
        return requested;
    }

    private UUID hiringManager(UUID requested) {
        if (requested == null) {
            return null;
        }
        boolean ok = jdbc.sql("""
                        select exists (select 1 from hr_users where id = :u and company_id = :c and status <> 'disabled'
                                          and role in ('admin', 'recruiter', 'hiring_manager'))
                        """)
                .param("u", requested).param("c", CurrentUser.companyId()).query(Boolean.class).single();
        if (!ok) {
            throw ApiException.badRequest("invalid_hiring_manager", "Choose the hiring manager from your team's logins.");
        }
        return requested;
    }

    private static void requireReason(String reason, String what) {
        if (reason == null || reason.length() < 5) {
            throw ApiException.badRequest("reason_required", "Add a short reason for " + what + " (at least 5 characters).");
        }
    }

    /** Saves tidy, de-duplicated criteria; refuses the same keyword in two lists. */
    private void saveCriteria(Job job, List<CriterionInput> input, int version) {
        if (input == null) {
            return;
        }
        List<CriteriaAdvisor.Item> items = advisor.check(job.getCompanyId(), input);
        items.stream().filter(CriteriaAdvisor.Item::blocking).findFirst().ifPresent(item -> {
            throw ApiException.badRequest("duplicate_keyword", item.warning());
        });
        for (CriteriaAdvisor.Item item : items) {
            // findFirst before map: most criteria have no minimum years (null).
            Integer minYears = input.stream()
                    .filter(c -> c.kind() == item.kind() && c.value().strip().equalsIgnoreCase(item.value()))
                    .findFirst().map(CriterionInput::minYears).orElse(null);
            criteria.save(new JobCriterion(job.getId(), item.kind(), item.display(), minYears, version));
        }
    }

    private boolean sameCriteria(Job job, List<CriterionInput> input) {
        SkillDictionary.Vocabulary vocabulary = dictionary.vocabulary(job.getCompanyId());
        Set<String> current = criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(job.getId()).stream()
                .map(c -> c.getKind() + ":" + c.getValue().toLowerCase(Locale.ROOT) + ":" + c.getMinYears())
                .collect(Collectors.toSet());
        Set<String> wanted = input.stream()
                .map(c -> c.kind() + ":" + (c.kind() == CriterionKind.CERTIFICATION ? c.value().strip()
                        : vocabulary.display(c.value())).toLowerCase(Locale.ROOT) + ":"
                        + (c.kind() == CriterionKind.MUST_HAVE ? c.minYears() : null))
                .collect(Collectors.toSet());
        return current.equals(wanted);
    }

    private JobDetail toDetail(Job job) {
        SkillDictionary.Vocabulary vocabulary = dictionary.vocabulary(job.getCompanyId());
        List<CriterionView> criterionViews = criteria.findByJobIdAndRetiredAtIsNullOrderByKindAscCreatedAtAsc(job.getId())
                .stream()
                .map(c -> new CriterionView(c.getId(), c.getKind(), c.getValue(), c.getMinYears(),
                        c.getKind() == CriterionKind.CERTIFICATION ? List.of() : vocabulary.alsoMatches(c.getValue())))
                .toList();
        List<RoundView> roundViews = rounds.findByJobIdOrderByRound(job.getId()).stream()
                .map(r -> new RoundView(r.getRound(), r.getName(), r.getKind(), r.getMode(), r.getDurationMin(),
                        r.getTargetDays()))
                .toList();
        Counts counts = countsFor(job.getCompanyId(), job.getId()).getOrDefault(job.getId(), new Counts(0, 0, 0, 0, 0));
        Changes changes = changeLog.forRecord("jobs", job.getId());
        UUID covering = access.coveringFor(job.getOwnerId(), job.getHiringManagerId());
        return new JobDetail(job.getId(), job.getJobRef(), job.getTitle(), job.getLocation(), job.getContractType(),
                job.getOpenings(), job.getMinExperienceYrs(), job.getStatus(), job.getPublishedAt(),
                job.getShortlistConfirmedAt(), job.getCreatedAt(), name(job.getCreatedBy()), criterionViews, roundViews,
                counts, counts.cvs() > 0, job.getCriteriaVersion(), job.getMaxNoticeDays(), job.getMaxCtcLpa(),
                job.getAcceptedLocations(), pipeline(job.getId()), job.isAiShortlisting(), job.getDescription(),
                job.getDescriptionFileName(), person(job.getOwnerId(), name(job.getOwnerId())),
                person(job.getHiringManagerId(), name(job.getHiringManagerId())), job.getStatusReason(),
                job.getStatusChangedAt(), name(job.getStatusChangedBy()), lockedUpTo(job.getId()),
                changes.createdAt() == null ? new Changes(name(job.getCreatedBy()), job.getCreatedAt(), null, null) : changes,
                covering == null ? null : name(covering), starred(job.getId()));
    }

    private String name(UUID userId) {
        return userId == null ? null : users.findById(userId).map(u -> u.getFullName()).orElse(null);
    }

    private static PersonRef person(UUID id, String name) {
        return id == null ? null : new PersonRef(id, name);
    }

    private Pipeline pipeline(UUID jobId) {
        record Totals(int screening, int shortlisted, int offer, int hired, int rejected, int withdrawn) {
        }
        Totals totals = jdbc.sql("""
                        select count(*) filter (where a.status = 'active' and a.stage = 'screening'
                                                  and coalesce(d.decision::text, '') <> 'rejected') as screening,
                               count(*) filter (where a.status = 'active' and a.stage = 'shortlist') as shortlisted,
                               count(*) filter (where a.status = 'active' and a.stage = 'offer') as offer,
                               count(*) filter (where a.status = 'hired') as hired,
                               count(*) filter (where a.status = 'rejected' or (a.status = 'active' and a.stage = 'screening'
                                                  and d.decision = 'rejected')) as rejected,
                               count(*) filter (where a.status = 'withdrawn') as withdrawn
                          from applications a
                          left join lateral (select decision from shortlist_decisions where application_id = a.id
                                              order by created_at desc limit 1) d on true
                         where a.job_id = :job
                        """)
                .param("job", jobId)
                .query((rs, row) -> new Totals(rs.getInt("screening"), rs.getInt("shortlisted"), rs.getInt("offer"),
                        rs.getInt("hired"), rs.getInt("rejected"), rs.getInt("withdrawn")))
                .single();
        return new Pipeline(totals.screening(), totals.shortlisted(), roundCounts(jobId).getOrDefault(jobId, List.of()),
                totals.offer(), totals.hired(), totals.rejected(), totals.withdrawn());
    }

    /** Candidates in each round of each visible job (or one job), with how many are past the target time. */
    private Map<UUID, List<RoundCount>> roundCounts(UUID jobId) {
        Map<UUID, List<RoundCount>> out = new HashMap<>();
        access.bind(jdbc.sql("""
                        select jr.job_id, jr.round, jr.name, jr.kind,
                               count(a.id) as n,
                               count(a.id) filter (where jr.target_days is not null and a.stage_entered_at is not null
                                   and a.stage_entered_at + make_interval(days => jr.target_days) < now()) as overdue
                          from job_rounds jr
                          join jobs j on j.id = jr.job_id
                          left join applications a on a.job_id = jr.job_id and a.status = 'active'
                               and (a.stage::text = 'round' || jr.round or (jr.round = 1 and a.stage = 'shortlist'))
                         where j.company_id = :companyId
                           and (:allJobs or j.owner_id in (:people) or j.hiring_manager_id in (:people))
                           and (cast(:job as uuid) is null or j.id = :job)
                         group by jr.job_id, jr.round, jr.name, jr.kind
                         order by jr.job_id, jr.round
                        """))
                .param("job", jobId)
                .query((rs, n) -> out.computeIfAbsent(Rows.uuid(rs, "job_id"), k -> new ArrayList<>())
                        .add(new RoundCount(rs.getInt("round"), rs.getString("name"),
                                Rows.enumOf(rs, "kind", RoundKind.class), rs.getInt("n"), rs.getInt("overdue"))))
                .list();
        return out;
    }

    private Map<UUID, Counts> countsFor(UUID companyId, UUID jobId) {
        record Row(UUID jobId, Counts counts) {
        }
        return jdbc.sql("""
                        select j.id,
                               count(a.id) as cvs,
                               count(s.id) as screened,
                               count(s.id) filter (where s.suggestion = 'shortlist') as ai_shortlist,
                               count(d.id) filter (where d.decision = 'shortlisted') as shortlisted,
                               (select count(*) from interviews i join applications a2 on a2.id = i.application_id
                                 where a2.job_id = j.id and i.status not in ('cancelled', 'draft')) as interviews
                          from jobs j
                          left join applications a on a.job_id = j.id
                          left join lateral (select id, suggestion from ai_screenings where application_id = a.id
                                              order by created_at desc limit 1) s on true
                          left join lateral (select id, decision from shortlist_decisions where application_id = a.id
                                              order by created_at desc limit 1) d on true
                         where j.company_id = :companyId and (cast(:jobId as uuid) is null or j.id = :jobId)
                         group by j.id
                        """)
                .param("companyId", companyId)
                .param("jobId", jobId)
                .query((rs, row) -> new Row(rs.getObject("id", UUID.class), new Counts(rs.getInt("cvs"),
                        rs.getInt("screened"), rs.getInt("ai_shortlist"), rs.getInt("shortlisted"), rs.getInt("interviews"))))
                .list().stream()
                .collect(Collectors.toMap(Row::jobId, Row::counts, (a, b) -> a));
    }

    private String nextJobRef(UUID companyId) {
        String name = companies.findById(companyId).map(c -> c.getName()).orElse("ETT");
        String prefix = Arrays.stream(name.split("[^A-Za-z]+"))
                .filter(word -> !word.isEmpty())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT))
                .limit(3)
                .collect(Collectors.joining());
        if (prefix.length() < 2) {
            prefix = name.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
            prefix = prefix.substring(0, Math.min(3, Math.max(prefix.length(), 0)));
        }
        return "%s-%d-%04d".formatted(prefix.isEmpty() ? "JOB" : prefix, Year.now(clock).getValue(), jobs.nextRefNumber());
    }

    private static List<String> tidyList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::strip).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    static <E extends Enum<E>> E enumOf(Class<E> type, String label) {
        return label == null ? null : Enum.valueOf(type, label.toUpperCase(Locale.ROOT));
    }

    static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
