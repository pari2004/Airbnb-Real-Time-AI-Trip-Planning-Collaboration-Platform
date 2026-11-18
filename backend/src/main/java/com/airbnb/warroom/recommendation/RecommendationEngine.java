package com.airbnb.warroom.recommendation;

import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid recommender (PRD Epic 3): content-based similarity (destination/category/dietary affinity)
 * blended with user-based collaborative filtering (co-travel / shared taste). Pure Java, no external
 * ML library. Deterministic given its inputs, so it is directly unit-testable (Precision@5).
 */
@Component
public class RecommendationEngine {

    /** Blend weight: content vs collaborative. */
    private static final double ALPHA = 0.6;
    private static final int NEIGHBOURS = 10;

    public record ScoredVenue(Long venueId, String destination, double score) {
    }

    /** Trains on the interaction log and returns a ranked venue list per user. */
    public Map<Long, List<ScoredVenue>> train(List<Interaction> interactions, List<Venue> venues) {
        Map<Long, Map<String, Double>> userVectors = buildUserVectors(interactions);
        Map<Long, Set<Long>> dislikedByUser = dislikedVenues(interactions);

        Map<Long, List<ScoredVenue>> result = new HashMap<>();
        for (Long userId : userVectors.keySet()) {
            result.put(userId, rankForUser(userId, userVectors, venues, dislikedByUser.getOrDefault(userId, Set.of())));
        }
        return result;
    }

    public List<ScoredVenue> rankForUser(Long userId, Map<Long, Map<String, Double>> userVectors,
                                         List<Venue> venues, Set<Long> disliked) {
        Map<String, Double> self = userVectors.getOrDefault(userId, Map.of());

        // Precompute neighbour similarities for the CF term.
        List<Map.Entry<Long, Double>> neighbours = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> e : userVectors.entrySet()) {
            if (e.getKey().equals(userId)) {
                continue;
            }
            double sim = cosine(self, e.getValue());
            if (sim > 0) {
                neighbours.add(Map.entry(e.getKey(), sim));
            }
        }
        neighbours.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
        if (neighbours.size() > NEIGHBOURS) {
            neighbours = neighbours.subList(0, NEIGHBOURS);
        }

        // Raw content + CF scores per venue.
        Map<Long, Double> content = new HashMap<>();
        Map<Long, Double> cf = new HashMap<>();
        for (Venue v : venues) {
            if (disliked.contains(v.id())) {
                continue;
            }
            Map<String, Double> vv = venueVector(v);
            content.put(v.id(), cosine(self, vv));
            cf.put(v.id(), collaborativeScore(v, neighbours, userVectors));
        }

        Map<Long, Double> contentNorm = minMax(content);
        Map<Long, Double> cfNorm = minMax(cf);

        List<ScoredVenue> ranked = new ArrayList<>();
        for (Venue v : venues) {
            if (disliked.contains(v.id())) {
                continue;
            }
            double score = ALPHA * contentNorm.getOrDefault(v.id(), 0.0)
                    + (1 - ALPHA) * cfNorm.getOrDefault(v.id(), 0.0);
            ranked.add(new ScoredVenue(v.id(), v.destination(), score));
        }
        ranked.sort(Comparator.comparingDouble(ScoredVenue::score).reversed()
                .thenComparing(ScoredVenue::venueId));
        return ranked;
    }

    private double collaborativeScore(Venue v, List<Map.Entry<Long, Double>> neighbours,
                                      Map<Long, Map<String, Double>> userVectors) {
        String catKey = "cat:" + v.category().toLowerCase();
        double num = 0;
        double den = 0;
        for (Map.Entry<Long, Double> n : neighbours) {
            double interest = Math.max(0.0, userVectors.get(n.getKey()).getOrDefault(catKey, 0.0));
            num += n.getValue() * interest;
            den += n.getValue();
        }
        return den > 0 ? num / den : 0.0;
    }

    public Map<Long, Map<String, Double>> buildUserVectors(List<Interaction> interactions) {
        Map<Long, Map<String, Double>> vectors = new HashMap<>();
        for (Interaction in : interactions) {
            double w = in.isLiked() ? 1.0 : -0.5;
            Map<String, Double> vec = vectors.computeIfAbsent(in.getUserId(), k -> new HashMap<>());
            vec.merge("cat:" + in.getCategory().toLowerCase(), w, Double::sum);
            vec.merge("dest:" + in.getDestination().toLowerCase(), w * 0.5, Double::sum);
        }
        return vectors;
    }

    private Map<Long, Set<Long>> dislikedVenues(List<Interaction> interactions) {
        Map<Long, Set<Long>> out = new HashMap<>();
        for (Interaction in : interactions) {
            if (!in.isLiked() && in.getVenueId() != null) {
                out.computeIfAbsent(in.getUserId(), k -> new HashSet<>()).add(in.getVenueId());
            }
        }
        return out;
    }

    private Map<String, Double> venueVector(Venue v) {
        Map<String, Double> vec = new HashMap<>();
        vec.put("cat:" + v.category().toLowerCase(), 1.0);
        vec.put("dest:" + v.destination().toLowerCase(), 0.5);
        for (String tag : v.dietaryTags()) {
            vec.merge("tag:" + tag.toLowerCase(), 0.3, Double::sum);
        }
        return vec;
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        double dot = 0;
        Map<String, Double> small = a.size() < b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double o = large.get(e.getKey());
            if (o != null) {
                dot += e.getValue() * o;
            }
        }
        double na = norm(a);
        double nb = norm(b);
        return (na == 0 || nb == 0) ? 0.0 : dot / (na * nb);
    }

    private double norm(Map<String, Double> v) {
        double s = 0;
        for (double w : v.values()) {
            s += w * w;
        }
        return Math.sqrt(s);
    }

    private Map<Long, Double> minMax(Map<Long, Double> scores) {
        if (scores.isEmpty()) {
            return scores;
        }
        double min = scores.values().stream().mapToDouble(d -> d).min().orElse(0);
        double max = scores.values().stream().mapToDouble(d -> d).max().orElse(0);
        double range = max - min;
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            out.put(e.getKey(), range == 0 ? 0.0 : (e.getValue() - min) / range);
        }
        return out;
    }
}
