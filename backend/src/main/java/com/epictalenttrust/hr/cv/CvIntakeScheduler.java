package com.epictalenttrust.hr.cv;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Safety net for the CV queue: picks up anything left after a restart and checks the 10-minute target. */
@Component
@ConditionalOnProperty(prefix = "ett.scheduling", name = "jobs-enabled", havingValue = "true", matchIfMissing = true)
public class CvIntakeScheduler {

    private final CvIntakeWorker worker;

    public CvIntakeScheduler(CvIntakeWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT20S")
    public void poll() {
        worker.drain();
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void overdue() {
        worker.checkOverdue();
    }
}
