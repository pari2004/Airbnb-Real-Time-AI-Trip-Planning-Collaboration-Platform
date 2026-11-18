package com.airbnb.warroom.auth;

import com.airbnb.warroom.common.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Validates the {@code Authorization: Bearer <jwt>} header on every HTTP request and populates the
 * reactive security context with an {@link AuthPrincipal} and {@code ROLE_*} authority. A present
 * but invalid token is rejected with 401; a missing token proceeds as anonymous (public routes).
 *
 * <p>Registered at {@code SecurityWebFiltersOrder.AUTHENTICATION} by {@code SecurityConfig} (not a
 * standalone bean) so it runs before authorization within the security chain.
 */
public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtProvider jwtProvider;

    public JwtAuthenticationWebFilter(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = header.substring(7);
        AuthPrincipal principal;
        try {
            principal = jwtProvider.parse(token);
        } catch (ApiException e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        var authority = new SimpleGrantedAuthority("ROLE_" + principal.role().toUpperCase());
        var auth = new UsernamePasswordAuthenticationToken(principal, token, List.of(authority));
        return chain.filter(exchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }
}
