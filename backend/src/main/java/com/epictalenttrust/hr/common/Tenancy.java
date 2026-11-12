package com.epictalenttrust.hr.common;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.interview.Interview;
import com.epictalenttrust.hr.interview.InterviewRepository;
import com.epictalenttrust.hr.interview.RescheduleRequest;
import com.epictalenttrust.hr.interview.RescheduleRequestRepository;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Loads records only if the signed-in person may see them: same company, and (for recruiters and hiring managers)
 * one of their own jobs or a job of someone they are covering. Anything else is reported as "not found" so its
 * existence isn't revealed. Work done on a covered job is recorded in the audit trail as "covering for …".
 */
@Component
public class Tenancy {

    private final JobRepository jobs;
    private final JobApplicationRepository applications;
    private final InterviewRepository interviews;
    private final RescheduleRequestRepository rescheduleRequests;
    private final com.epictalenttrust.hr.security.Access access;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    public Tenancy(JobRepository jobs, JobApplicationRepository applications, InterviewRepository interviews,
                   RescheduleRequestRepository rescheduleRequests, com.epictalenttrust.hr.security.Access access,
                   org.springframework.jdbc.core.simple.JdbcClient jdbc) {
        this.jobs = jobs;
        this.applications = applications;
        this.interviews = interviews;
        this.rescheduleRequests = rescheduleRequests;
        this.access = access;
        this.jdbc = jdbc;
    }

    public Job job(UUID jobId) {
        return visible(jobs.findByIdAndCompanyId(jobId, CurrentUser.companyId()).orElse(null), "Job");
    }

    public JobApplication application(UUID applicationId) {
        JobApplication application = applications.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Candidate"));
        visible(jobs.findByIdAndCompanyId(application.getJobId(), CurrentUser.companyId()).orElse(null), "Candidate");
        return application;
    }

    private Job visible(Job job, String what) {
        if (job == null || !access.canSee(job.getOwnerId(), job.getHiringManagerId())) {
            throw ApiException.notFound(what);
        }
        UUID coveringFor = access.coveringFor(job.getOwnerId(), job.getHiringManagerId());
        if (coveringFor != null
                && org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            jdbc.sql("select set_config('app.on_behalf_of', :id, true)").param("id", coveringFor.toString())
                    .query(String.class).single();
        }
        return job;
    }

    public Interview interview(UUID interviewId) {
        Interview interview = interviews.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview"));
        application(interview.getApplicationId());
        return interview;
    }

    public RescheduleRequest rescheduleRequest(UUID requestId) {
        RescheduleRequest request = rescheduleRequests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Reschedule request"));
        interview(request.getInterviewId());
        return request;
    }
}
