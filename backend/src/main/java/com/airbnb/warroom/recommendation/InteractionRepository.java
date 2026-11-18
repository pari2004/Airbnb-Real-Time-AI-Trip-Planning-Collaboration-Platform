package com.airbnb.warroom.recommendation;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface InteractionRepository extends ReactiveCrudRepository<Interaction, Long> {
    Flux<Interaction> findByUserId(Long userId);
}
