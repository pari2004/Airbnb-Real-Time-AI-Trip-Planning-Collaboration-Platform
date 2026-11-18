package com.airbnb.warroom.trip;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface TripRepository extends ReactiveCrudRepository<Trip, Long> {
    Mono<Trip> findByInviteCode(String inviteCode);

    Mono<Boolean> existsByInviteCode(String inviteCode);
}
