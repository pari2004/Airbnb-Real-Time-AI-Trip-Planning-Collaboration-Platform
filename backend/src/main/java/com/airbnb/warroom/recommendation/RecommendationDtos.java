package com.airbnb.warroom.recommendation;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/** Recommendation + interaction payloads (PRD Epic 3). */
public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    public record RecommendationView(String venueId, String name, String destination, String category,
                                     BigDecimal pricePerPerson, double score, int rank) {
    }

    public record InteractionRequest(
            @NotBlank String destination,
            @NotBlank String category,
            String venueId,
            Boolean liked,
            String tripId,
            List<String> coTraveledWith) {
    }

    public record RetrainResult(int usersTrained, int recommendationsWritten) {
    }
}
