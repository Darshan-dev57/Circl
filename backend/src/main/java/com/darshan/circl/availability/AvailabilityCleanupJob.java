package com.darshan.circl.availability;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityCleanupJob {

    private final AvailabilityService service;

    public AvailabilityCleanupJob(AvailabilityService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT1M")
    @SchedulerLock(name = "availability-cleanup", lockAtMostFor = "PT2M")
    public void run() {
        service.cleanupExpired();
    }
}
