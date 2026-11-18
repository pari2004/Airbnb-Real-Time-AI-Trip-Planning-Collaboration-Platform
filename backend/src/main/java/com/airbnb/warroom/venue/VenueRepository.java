package com.airbnb.warroom.venue;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface VenueRepository extends ReactiveCrudRepository<VenueMetadata, Long> {
    Flux<VenueMetadata> findByDestination(String destination);
}
