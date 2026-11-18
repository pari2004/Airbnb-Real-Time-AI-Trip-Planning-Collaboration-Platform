package com.airbnb.warroom.recommendation;

import com.airbnb.warroom.recommendation.RecommendationEngine.ScoredVenue;
import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline evaluation of the recommender (PRD KPI: Precision@5 >= 0.6 on a held-out set). A
 * recommended venue counts as relevant if it shares a (destination, category) the user liked in the
 * held-out interactions — a content-relevance definition suited to a small authored catalogue.
 */
@Component
public class RecommendationEvaluator {

    public double precisionAtK(List<ScoredVenue> ranked, Set<Long> relevantVenueIds, int k) {
        if (ranked.isEmpty() || k <= 0) {
            return 0.0;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (relevantVenueIds.contains(ranked.get(i).venueId())) {
                hits++;
            }
        }
        return (double) hits / k;
    }

    /** Builds the relevant-venue set from held-out liked interactions, by (destination, category). */
    public Set<Long> relevantVenues(List<Interaction> heldOutLiked, List<Venue> venues) {
        Set<String> likedKeys = new HashSet<>();
        for (Interaction in : heldOutLiked) {
            if (in.isLiked()) {
                likedKeys.add(key(in.getDestination(), in.getCategory()));
            }
        }
        Set<Long> relevant = new HashSet<>();
        for (Venue v : venues) {
            if (likedKeys.contains(key(v.destination(), v.category()))) {
                relevant.add(v.id());
            }
        }
        return relevant;
    }

    private String key(String destination, String category) {
        return destination.toLowerCase() + "::" + category.toLowerCase();
    }
}
