package com.airbnb.warroom.auth;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {
    Mono<RefreshToken> findByTokenHash(String tokenHash);

    Mono<Void> deleteByUserId(Long userId);
}
