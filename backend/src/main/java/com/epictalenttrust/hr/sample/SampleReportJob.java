package com.epictalenttrust.hr.sample;

import com.epictalenttrust.hr.security.ActorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Fills in sample reports for interviews that have finished, when sample reports are on. */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class SampleReportJob {

    private static final Logger log = LoggerFactory.getLogger(SampleReportJob.class);

    private final SampleReportService samples;

    public SampleReportJob(SampleReportService samples) {
        this.samples = samples;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public void run() {
        for (UUID interviewId : samples.finishedWithoutReport()) {
            try {
                ActorContext.runAs(ActorContext.Actor.SYSTEM, () -> samples.completeFinished(interviewId));
            } catch (RuntimeException e) {
                log.warn("Could not create a sample report for interview {}: {}", interviewId, e.getMessage());
            }
        }
    }
}
