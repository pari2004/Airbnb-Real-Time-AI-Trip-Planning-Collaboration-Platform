package com.airbnb.warroom.config;

import com.airbnb.warroom.auth.AuthService;
import com.airbnb.warroom.venue.VenueDtos.VenueUploadRequest;
import com.airbnb.warroom.venue.VenueRepository;
import com.airbnb.warroom.venue.VenueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * Idempotent dev seeder: provisions a demo admin + two travellers (so all four RBAC roles can be
 * exercised) and loads the authored destination dataset, only when empty. Disable with SEED_ENABLED=false.
 */
@Component
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    private final AppProperties props;
    private final AuthService authService;
    private final VenueService venueService;
    private final VenueRepository venueRepo;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public SeedRunner(AppProperties props, AuthService authService, VenueService venueService,
                      VenueRepository venueRepo, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.props = props;
        this.authService = authService;
        this.venueService = venueService;
        this.venueRepo = venueRepo;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            log.info("Seeding disabled (app.seed.enabled=false)");
            return;
        }
        try {
            seedUsers()
                    .then(seedVenues())
                    .block();
            log.info("Seeding complete. Demo logins: admin@warroom.test / Admin123! ,"
                    + " leader@warroom.test / Leader123! , traveler@warroom.test / Traveler123!");
        } catch (Exception e) {
            log.warn("Seeding skipped/failed: {}", e.getMessage());
        }
    }

    private Mono<Void> seedUsers() {
        return authService.ensureUser("admin@warroom.test", "Admin123!", "War Room Admin",
                        "admin", new BigDecimal("500"), List.of())
                .then(authService.ensureUser("leader@warroom.test", "Leader123!", "Trip Leader Demo",
                        "planner", new BigDecimal("150"), List.of("vegetarian")))
                .then(authService.ensureUser("traveler@warroom.test", "Traveler123!", "Co-Traveler Demo",
                        "planner", new BigDecimal("40"), List.of("vegan")))
                .then();
    }

    private Mono<Void> seedVenues() {
        return venueRepo.findByDestination("Lisbon, Portugal").hasElements()
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        log.info("Venue dataset already present; skipping seed");
                        return Mono.empty();
                    }
                    return Mono.fromCallable(this::readDataset)
                            .flatMap(venueService::uploadDataset)
                            .doOnNext(r -> log.info("Seeded {} venues for {}", r.inserted(), r.destination()))
                            .then();
                });
    }

    private VenueUploadRequest readDataset() {
        try {
            Resource resource = resourceLoader.getResource(props.seed().destinationFile());
            try (var in = resource.getInputStream()) {
                return objectMapper.readValue(in, VenueUploadRequest.class);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not read seed dataset: " + e.getMessage(), e);
        }
    }
}
