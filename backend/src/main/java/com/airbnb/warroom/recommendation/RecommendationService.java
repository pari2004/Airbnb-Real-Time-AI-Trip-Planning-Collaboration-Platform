package com.airbnb.warroom.recommendation;

import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.recommendation.RecommendationDtos.InteractionRequest;
import com.airbnb.warroom.recommendation.RecommendationDtos.RecommendationView;
import com.airbnb.warroom.recommendation.RecommendationDtos.RetrainResult;
import com.airbnb.warroom.recommendation.RecommendationEngine.ScoredVenue;
import com.airbnb.warroom.venue.Venue;
import com.airbnb.warroom.venue.VenueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Offline-trained recommendation materialization + serving (PRD Epic 3). Training runs as a batch
 * job (scheduled, on new interactions, or admin-triggered) and writes the {@code recommendations}
 * table; the rail reads that materialized output, so it updates without a retrain-and-redeploy.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int TOP_N = 20;

    private final InteractionRepository interactions;
    private final RecommendationRepository recommendations;
    private final RecommendationEngine engine;
    private final VenueService venueService;

    public RecommendationService(InteractionRepository interactions, RecommendationRepository recommendations,
                                 RecommendationEngine engine, VenueService venueService) {
        this.interactions = interactions;
        this.recommendations = recommendations;
        this.engine = engine;
        this.venueService = venueService;
    }

    /** Records an interaction and asynchronously refreshes recommendations (batch retrain). */
    public Mono<Interaction> record(Long userId, InteractionRequest req) {
        Interaction in = new Interaction();
        in.setUserId(userId);
        in.setTripId(req.tripId() == null ? null : PublicId.decode(req.tripId()));
        in.setDestination(req.destination());
        in.setCategory(req.category());
        in.setVenueId(req.venueId() == null ? null : PublicId.decode(req.venueId()));
        in.setLiked(req.liked() == null || req.liked());
        Long[] co = req.coTraveledWith() == null ? new Long[0]
                : req.coTraveledWith().stream().map(PublicId::decode).toArray(Long[]::new);
        in.setCoTraveledWith(co);
        return interactions.save(in)
                .doOnSuccess(saved -> retrain()
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(r -> log.info("Recommendations refreshed: {}", r),
                                e -> log.warn("Retrain failed: {}", e.getMessage())));
    }

    /** Batch retrain: rebuild the materialized recommendations table from scratch. */
    public Mono<RetrainResult> retrain() {
        return Mono.zip(interactions.findAll().collectList(), venueService.loadAll())
                .flatMap(t -> {
                    List<Interaction> allInteractions = t.getT1();
                    List<Venue> venues = t.getT2();
                    Map<Long, List<ScoredVenue>> perUser = engine.train(allInteractions, venues);
                    AtomicInteger written = new AtomicInteger();
                    return Flux.fromIterable(perUser.entrySet())
                            .concatMap(e -> persistForUser(e.getKey(), e.getValue(), written))
                            .then(Mono.fromSupplier(() -> new RetrainResult(perUser.size(), written.get())));
                });
    }

    private Mono<Void> persistForUser(Long userId, List<ScoredVenue> ranked, AtomicInteger counter) {
        List<ScoredVenue> top = ranked.subList(0, Math.min(TOP_N, ranked.size()));
        return recommendations.deleteByUserId(userId)
                .thenMany(Flux.range(0, top.size()).concatMap(i -> {
                    ScoredVenue sv = top.get(i);
                    Recommendation r = new Recommendation(userId, sv.venueId(), sv.destination(),
                            BigDecimal.valueOf(sv.score()).setScale(5, java.math.RoundingMode.HALF_UP), i + 1);
                    counter.incrementAndGet();
                    return recommendations.save(r);
                }))
                .then();
    }

    /** Serves the materialized "Recommended for Your Group" rail for a user. */
    public Mono<List<RecommendationView>> forUser(Long userId) {
        return recommendations.findByUserIdOrderByRankAsc(userId).collectList()
                .flatMap(recs -> {
                    if (recs.isEmpty()) {
                        return Mono.just(List.<RecommendationView>of());
                    }
                    return venueService.loadAll().map(venues -> {
                        Map<Long, Venue> byId = venues.stream().collect(Collectors.toMap(Venue::id, v -> v));
                        return recs.stream()
                                .map(r -> toView(r, byId.get(r.getVenueId())))
                                .filter(java.util.Objects::nonNull)
                                .toList();
                    });
                });
    }

    private RecommendationView toView(Recommendation r, Venue v) {
        if (v == null) {
            return null;
        }
        return new RecommendationView(
                PublicId.encode(PublicId.VENUE, v.id()),
                v.name(), v.destination(), v.category(), v.pricePerPerson(),
                r.getScore().doubleValue(), r.getRank());
    }
}
