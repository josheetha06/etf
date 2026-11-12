package com.epictalenttrust.hr.interview;

import com.epictalenttrust.hr.application.JobApplicationRepository;
import com.epictalenttrust.hr.job.JobRepository;
import com.epictalenttrust.hr.job.JobStatus;
import com.epictalenttrust.hr.message.MessageService;
import com.epictalenttrust.hr.message.MessageTemplates;
import com.epictalenttrust.hr.message.SenderKind;
import com.epictalenttrust.hr.security.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Sends one reminder in the 24 hours before an invitation's reply-by deadline. Jobs on hold send no reminders;
 * they start again when the job is opened.
 */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final InterviewRepository interviews;
    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final MessageService messages;
    private final MessageTemplates templates;
    private final InterviewFacts facts;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public ReminderJob(InterviewRepository interviews, JobApplicationRepository applications, JobRepository jobs,
                       MessageService messages,
                       MessageTemplates templates, InterviewFacts facts, PlatformTransactionManager transactionManager,
                       Clock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.jobs = jobs;
        this.messages = messages;
        this.templates = templates;
        this.facts = facts;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    public void sendDueReminders() {
        ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> transaction.executeWithoutResult(status -> {
            Instant now = clock.instant();
            int sent = 0;
            for (Interview interview : interviews.findDueForReminder(now, now.plus(Duration.ofHours(24)))) {
                if (interview.getInvitedAt() != null && interview.getInvitedAt().isAfter(now.minus(Duration.ofHours(6)))) {
                    continue;
                }
                var application = applications.findById(interview.getApplicationId()).orElseThrow();
                if (jobs.findById(application.getJobId()).map(j -> j.getStatus() != JobStatus.OPEN
                        && j.getStatus() != JobStatus.SHORTLIST_CONFIRMED).orElse(true)) {
                    // On hold (or closed): reminders wait until the job is open again.
                    continue;
                }
                messages.emailCandidate(application, interview.getId(), SenderKind.SYSTEM, null,
                        templates.reminder(facts.of(interview, application)));
                interview.markReminderSent(now);
                sent++;
            }
            if (sent > 0) {
                log.info("Sent {} interview reply reminders", sent);
            }
        }));
    }
}
