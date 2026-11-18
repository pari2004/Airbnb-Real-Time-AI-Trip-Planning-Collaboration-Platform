package com.airbnb.warroom.analytics;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface SessionEventRepository extends ReactiveCrudRepository<SessionEvent, Long> {
    Flux<SessionEvent> findByTripIdOrderByCreatedAtAsc(Long tripId);

    Flux<SessionEvent> findByTripIdAndEventType(Long tripId, String eventType);
}
