package com.airbnb.warroom.chat;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatMessageRepository extends ReactiveCrudRepository<ChatMessage, Long> {
    Flux<ChatMessage> findByTripIdOrderByIdAsc(Long tripId);

    Mono<Long> countByTripIdAndMsgType(Long tripId, String msgType);
}
