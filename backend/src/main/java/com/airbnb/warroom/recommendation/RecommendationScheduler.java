package com.airbnb.warroom.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodic offline retrain (PRD Epic 3: an offline/batch retrain job is acceptable). */
@Component
public class RecommendationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecommendationScheduler.class);

    private final RecommendationService service;

    public RecommendationScheduler(RecommendationService service) {
        this.service = service;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void periodicRetrain() {
        service.retrain()
                .subscribe(r -> log.debug("Scheduled retrain: {}", r),
                        e -> log.warn("Scheduled retrain failed: {}", e.getMessage()));
    }
}
