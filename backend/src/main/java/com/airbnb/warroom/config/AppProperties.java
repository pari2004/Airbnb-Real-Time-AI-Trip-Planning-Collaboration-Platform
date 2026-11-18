package com.airbnb.warroom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Strongly-typed binding for the {@code app.*} config tree. */
@ConfigurationProperties("app")
public record AppProperties(Jwt jwt, Cors cors, Concierge concierge, Board board, Seed seed) {

    public record Jwt(String secret, long accessTokenTtlSeconds, long refreshTokenTtlSeconds, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Concierge(
            double proximityRadiusKm,
            int hoursBufferMinutes,
            double rankWeightProximity,
            double rankWeightRating,
            double rankWeightBudgetHeadroom,
            Llm llm) {

        public record Llm(String provider, String apiKey, String baseUrl, String model) {
        }
    }

    public record Board(long persistIntervalMs) {
    }

    public record Seed(boolean enabled, String destinationFile) {
    }
}
