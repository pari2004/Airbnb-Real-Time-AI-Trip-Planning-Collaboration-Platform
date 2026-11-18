package com.airbnb.warroom.recommendation;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RecommendationRepository extends ReactiveCrudRepository<Recommendation, Long> {
    Flux<Recommendation> findByUserIdOrderByRankAsc(Long userId);

    Mono<Void> deleteByUserId(Long userId);
}
