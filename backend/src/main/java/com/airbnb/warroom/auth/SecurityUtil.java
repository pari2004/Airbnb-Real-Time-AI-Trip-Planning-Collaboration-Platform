package com.airbnb.warroom.auth;

import com.airbnb.warroom.common.ApiException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

/** Convenience access to the current {@link AuthPrincipal} inside reactive controller/service chains. */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    public static Mono<AuthPrincipal> currentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (AuthPrincipal) ctx.getAuthentication().getPrincipal())
                .switchIfEmpty(Mono.error(ApiException.unauthorized("Not authenticated")));
    }

    public static Mono<Long> currentUserId() {
        return currentPrincipal().map(AuthPrincipal::userId);
    }
}
