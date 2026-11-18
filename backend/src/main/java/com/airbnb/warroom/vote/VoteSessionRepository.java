package com.airbnb.warroom.vote;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VoteSessionRepository extends ReactiveCrudRepository<VoteSession, Long> {
    Mono<VoteSession> findByItemIdAndStatus(Long itemId, String status);

    Flux<VoteSession> findByTripIdAndStatus(Long tripId, String status);

    Flux<VoteSession> findByStatusAndClosesAtBefore(String status, java.time.Instant cutoff);
}
