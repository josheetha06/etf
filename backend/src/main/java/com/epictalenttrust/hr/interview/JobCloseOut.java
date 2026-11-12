package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.activity.ActivityService;
import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What happens to candidates when a job closes. Every upcoming interview is cancelled (seats released, panel and
 * assessors told) and its candidate gets the "interview cancelled: position closed" email. Everyone else part-way
 * through (shortlisted, in a round or at offer) gets the "position closed" email. Candidates still being screened
 * haven't heard from the company, so they aren't emailed. Both emails can be reworded under Settings.
 */
@Component
public class JobCloseOut {

    /** What closing the job will do, shown in the close dialog before HR confirms. */
    public record Impact(int interviewsToCancel, int candidatesWithInterviews, int otherCandidates) {
    }

    private static final String UPCOMING = """
            select i.id from interviews i join applications a on a.id = i.application_id
             where a.job_id = :j and i.status in ('draft', 'invited', 'confirmed', 'reschedule_requested')
               and (i.start_at is null or i.start_at > now())
            """;

    private static final String IN_PROGRESS = """
            select a.id from applications a
             where a.job_id = :j and a.status = 'active' and a.stage <> 'screening'
            """;

    private final InterviewRepository interviews;
    private final InterviewService interviewService;
    private final JobApplicationRepository applications;
    private final CompanyRepository companies;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final ActivityService activity;
    private final JdbcClient jdbc;

    public JobCloseOut(InterviewRepository interviews, InterviewService interviewService,
                       JobApplicationRepository applications, CompanyRepository companies, MessageService messages,
                       MessageTemplates templates, ActivityService activity, JdbcClient jdbc) {
        this.interviews = interviews;
        this.interviewService = interviewService;
        this.applications = applications;
        this.companies = companies;
        this.messages = messages;
        this.templates = templates;
        this.activity = activity;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Impact impact(UUID jobId) {
        record Row(UUID applicationId, boolean sent) {
        }
        List<Row> upcoming = jdbc.sql("""
                        select i.application_id, i.status <> 'draft' as sent from interviews i
                          join applications a on a.id = i.application_id
                         where a.job_id = :j and i.status in ('draft', 'invited', 'confirmed', 'reschedule_requested')
                           and (i.start_at is null or i.start_at > now())
                        """)
                .param("j", jobId)
                .query((rs, n) -> new Row(rs.getObject("application_id", UUID.class), rs.getBoolean("sent")))
                .list();
        Set<UUID> withInterview = new HashSet<>();
        upcoming.stream().filter(Row::sent).forEach(r -> withInterview.add(r.applicationId()));
        Set<UUID> anyInterview = new HashSet<>();
        upcoming.forEach(r -> anyInterview.add(r.applicationId()));
        long others = jdbc.sql(IN_PROGRESS).param("j", jobId).query(UUID.class).list().stream()
                .filter(id -> !anyInterview.contains(id)).count();
        return new Impact((int) upcoming.stream().filter(Row::sent).count(), withInterview.size(), (int) others);
    }

    /** Runs inside the job's status change, so everything is saved together (or not at all). */
    @Transactional(propagation = Propagation.MANDATORY)
    public Impact closeOut(Job job, String reason) {
        Set<UUID> told = new HashSet<>();
        Set<UUID> touched = new HashSet<>();
        int cancelled = 0;
        for (UUID id : jdbc.sql(UPCOMING).param("j", job.getId()).query(UUID.class).list()) {
            Interview interview = interviews.findById(id).orElseThrow();
            touched.add(interview.getApplicationId());
            if (interviewService.cancelBecauseJobClosed(interview, reason)) {
                cancelled++;
                told.add(interview.getApplicationId());
            }
        }
        String company = companies.findById(job.getCompanyId()).map(c -> c.getName()).orElse("the hiring company");
        int others = 0;
        for (UUID applicationId : jdbc.sql(IN_PROGRESS).param("j", job.getId()).query(UUID.class).list()) {
            if (touched.contains(applicationId)) {
                continue;
            }
            JobApplication application = applications.findById(applicationId).orElseThrow();
            // A "you're through" email still waiting would now be wrong.
            jdbc.sql("""
                    update messages set cancelled_at = now(), next_attempt_at = null
                     where application_id = :a and sent_at is null and cancelled_at is null
                       and send_attempts = 0 and next_attempt_at > now()
                    """)
                    .param("a", applicationId).update();
            String firstName = jdbc.sql("select full_name from candidates where id = :c")
                    .param("c", application.getCandidateId()).query(String.class).optional()
                    .map(InterviewFacts::firstName).orElse("there");
            messages.emailCandidate(application, null, SenderKind.SYSTEM, null,
                    templates.positionClosed(job.getCompanyId(), firstName, job.getTitle(), company));
            activity.record("application.position_closed", "application", applicationId);
            others++;
        }
        return new Impact(cancelled, told.size(), others);
    }
}
