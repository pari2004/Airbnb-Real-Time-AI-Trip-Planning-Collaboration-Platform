package com.airbnb.warroom.common;

import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Assigns a correlation id per HTTP request (PRD 10.3 structured logging). Reuses an inbound
 * {@code X-Correlation-Id} header when present (so a Trip Room session can thread one id across
 * calls) and echoes it back on the response. The id is placed in the Reactor context and MDC.
 */
@Component
@Order(-1)
public class CorrelationIdFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String CONTEXT_KEY = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (incoming != null && !incoming.isBlank())
                ? incoming : UUID.randomUUID().toString();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(CONTEXT_KEY, correlationId))
                .doOnEach(signal -> {
                    if (signal.isOnNext() || signal.isOnError()) {
                        MDC.put(CONTEXT_KEY, correlationId);
                    }
                })
                .doFinally(s -> MDC.remove(CONTEXT_KEY));
    }
}
