package com.airbnb.warroom.common;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Lightweight health endpoints for the API layer and the board sync gateway
 * (PRD 10.3), suitable for a container orchestrator or uptime check.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    private final DatabaseClient db;

    public HealthController(DatabaseClient db) {
        this.db = db;
    }

    @GetMapping("/api")
    public Mono<Map<String, Object>> api() {
        return db.sql("SELECT 1").fetch().first()
                .map(r -> Map.<String, Object>of("status", "UP", "db", "UP"))
                .onErrorReturn(Map.of("status", "DEGRADED", "db", "DOWN"));
    }

    @GetMapping("/sync")
    public Mono<Map<String, Object>> sync() {
        // The WebSocket gateway runs in-process; report liveness of the reactive runtime.
        return Mono.just(Map.of("status", "UP", "gateway", "UP"));
    }
}
