package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.application.JobApplication;
import com.epictalenttrust.hr.candidate.Candidate;
import com.epictalenttrust.hr.candidate.CandidateRepository;
import com.epictalenttrust.hr.common.TimeZones;
import com.epictalenttrust.hr.company.CompanyRepository;
import com.epictalenttrust.hr.job.Job;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.message.MessageTemplates;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** Gathers what the email templates need to describe an interview. */
@Component
public class InterviewFacts {

    private final CandidateRepository candidates;
    private final JobRepository jobs;
    private final CompanyRepository companies;
    private final com.epictalenttrust.hr.job.JobRoundRepository rounds;

    public InterviewFacts(CandidateRepository candidates, JobRepository jobs, CompanyRepository companies,
                          com.epictalenttrust.hr.job.JobRoundRepository rounds) {
        this.candidates = candidates;
        this.jobs = jobs;
        this.companies = companies;
        this.rounds = rounds;
    }

    public MessageTemplates.InterviewFacts of(Interview interview, JobApplication application) {
        Candidate candidate = candidates.findById(application.getCandidateId()).orElseThrow();
        Job job = jobs.findById(application.getJobId()).orElseThrow();
        String company = companies.findById(job.getCompanyId()).map(c -> c.getName()).orElse("the hiring company");
        ZoneId zone = TimeZones.parseOr(interview.getCandidateTz(), TimeZones.forCountry(candidate.getCountry()));
        var plan = rounds.findByJobIdAndRound(job.getId(), interview.getRound()).orElse(null);
        return new MessageTemplates.InterviewFacts(job.getCompanyId(), firstName(candidate.getFullName()), job.getTitle(),
                company, interview.getRound(), plan == null ? null : plan.getName(),
                plan == null ? null : plan.getKind(), interview.getMode(), interview.getStartAt(), zone,
                interview.getDurationMin() == null ? 45 : interview.getDurationMin(),
                interview.getReplyBy() == null ? interview.getStartAt() : interview.getReplyBy(), null);
    }

    public static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.strip().split("\\s+")[0];
    }
}
