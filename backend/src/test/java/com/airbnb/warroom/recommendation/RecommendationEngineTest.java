package com.airbnb.warroom.recommendation;

import com.airbnb.warroom.recommendation.RecommendationEngine.ScoredVenue;
import com.airbnb.warroom.venue.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD KPI: Precision@5 >= 0.6 on a held-out interaction set. Users have a consistent category
 * preference; we train on part of their likes and hold out the rest, then verify the top-5 rail is
 * dominated by relevant (same-category) venues.
 */
class RecommendationEngineTest {

    private final RecommendationEngine engine = new RecommendationEngine();
    private final RecommendationEvaluator evaluator = new RecommendationEvaluator();
    private static final String DEST = "Lisbon, Portugal";

    private Venue venue(long id, String category) {
        return new Venue(id, DEST, "V" + id, category, BigDecimal.valueOf(20),
                LocalTime.of(10, 0), LocalTime.of(22, 0), 38.7, -9.1, 4.5, "", List.of());
    }

    private Interaction like(long user, String category, long venueId) {
        Interaction in = new Interaction();
        in.setUserId(user);
        in.setDestination(DEST);
        in.setCategory(category);
        in.setVenueId(venueId);
        in.setLiked(true);
        in.setCoTraveledWith(new Long[0]);
        return in;
    }

    @Test
    @DisplayName("Precision@5 of the hybrid recommender is >= 0.6 on held-out likes")
    void precisionAtFive() {
        // Catalogue: 6 dinner, 5 activity, plus a couple of others.
        List<Venue> venues = new ArrayList<>();
        long id = 1;
        List<Long> dinner = new ArrayList<>();
        List<Long> activity = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            venues.add(venue(id, "dinner"));
            dinner.add(id++);
        }
        for (int i = 0; i < 5; i++) {
            venues.add(venue(id, "activity"));
            activity.add(id++);
        }
        venues.add(venue(id++, "lodging"));
        venues.add(venue(id++, "cafe"));

        // Users 1-3 prefer dinner; users 4-6 prefer activity.
        Map<Long, String> prefs = Map.of(1L, "dinner", 2L, "dinner", 3L, "dinner",
                4L, "activity", 5L, "activity", 6L, "activity");

        List<Interaction> training = new ArrayList<>();
        Map<Long, List<Interaction>> heldOut = new java.util.HashMap<>();
        for (Map.Entry<Long, String> e : prefs.entrySet()) {
            long user = e.getKey();
            List<Long> pool = e.getValue().equals("dinner") ? dinner : activity;
            // Train on the first 3 likes, hold out the 4th.
            for (int i = 0; i < 3; i++) {
                training.add(like(user, e.getValue(), pool.get(i)));
            }
            heldOut.computeIfAbsent(user, k -> new ArrayList<>())
                    .add(like(user, e.getValue(), pool.get(3)));
        }

        Map<Long, List<ScoredVenue>> model = engine.train(training, venues);

        double totalPrecision = 0;
        for (Long user : prefs.keySet()) {
            Set<Long> relevant = evaluator.relevantVenues(heldOut.get(user), venues);
            double p = evaluator.precisionAtK(model.get(user), relevant, 5);
            totalPrecision += p;
        }
        double meanPrecision = totalPrecision / prefs.size();
        assertTrue(meanPrecision >= 0.6, "Mean Precision@5 was " + meanPrecision + " (expected >= 0.6)");
    }
}
