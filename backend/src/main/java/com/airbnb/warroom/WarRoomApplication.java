package com.airbnb.warroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Trip War Room — Collaborative Group Itinerary Canvas &amp; Real-Time Logistics Engine.
 *
 * <p>Stack: Spring Boot + Spring WebFlux (reactive REST + WebSocket) + Spring Data R2DBC
 * (non-blocking PostgreSQL) + Spring Security (JWT). Scheduling is enabled for the batched
 * board-state persistence job and the offline recommendation trainer.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WarRoomApplication {
    public static void main(String[] args) {
        SpringApplication.run(WarRoomApplication.class, args);
    }
}
