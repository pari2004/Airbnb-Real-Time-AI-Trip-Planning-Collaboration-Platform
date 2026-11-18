package com.airbnb.warroom.concierge;

import com.airbnb.warroom.common.PublicId;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** AI Concierge payloads (PRD 7.3.3). */
public final class ConciergeDtos {

    private ConciergeDtos() {
    }

    public record AskRequest(String userId, @NotBlank String question, String anchorItemId) {
    }

    public record CandidateView(String venueId, String name, double score, Double distanceKm,
                                BigDecimal pricePerPerson, List<String> dietaryTags) {
        public static CandidateView from(SuggestionCandidate c) {
            return new CandidateView(
                    PublicId.encode(PublicId.VENUE, c.venue().id()),
                    c.venue().name(),
                    round(c.score()),
                    c.distanceKm() == null ? null : round(c.distanceKm()),
                    c.venue().pricePerPerson(),
                    c.venue().dietaryTags());
        }

        private static double round(double d) {
            return Math.round(d * 1000.0) / 1000.0;
        }
    }

    /** Mirrors PRD 7.3.3 response, with extra transparency fields (candidates, constraintsApplied). */
    public record AiAnswer(String answer, String suggestedVenueId, List<String> sourceEvents,
                           Instant generatedAt, List<CandidateView> candidates,
                           List<String> constraintsApplied, boolean grounded) {
    }

    public record ConflictView(String conflictType, List<String> itemIds, String details) {
    }
}
