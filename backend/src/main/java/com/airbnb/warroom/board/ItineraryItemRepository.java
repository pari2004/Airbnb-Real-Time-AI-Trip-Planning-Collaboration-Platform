package com.airbnb.warroom.board;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;

public interface ItineraryItemRepository extends ReactiveCrudRepository<ItineraryItem, Long> {

    Flux<ItineraryItem> findByTripIdOrderByDayIndexAscStartTimeAsc(Long tripId);

    /**
     * Optimistic move: succeeds only if the caller's {@code position_version} still matches the
     * server's. Increments the version atomically. Returns rows affected (0 = version conflict).
     */
    @Modifying
    @Query("UPDATE itinerary_items SET day_index = :dayIndex, start_time = :startTime, " +
            "end_time = :endTime, position_version = position_version + 1 " +
            "WHERE id = :id AND position_version = :expectedVersion AND lifecycle_stage <> 'locked'")
    Mono<Integer> moveIfVersionMatches(Long id, int dayIndex, LocalTime startTime,
                                       LocalTime endTime, int expectedVersion);

    @Modifying
    @Query("UPDATE itinerary_items SET lifecycle_stage = :stage, position_version = position_version + 1 " +
            "WHERE id = :id")
    Mono<Integer> setLifecycleStage(Long id, String stage);

    @Modifying
    @Query("UPDATE itinerary_items SET venue_id = :venueId, lifecycle_stage = 'drafting', " +
            "position_version = position_version + 1 WHERE id = :id")
    Mono<Integer> applyVoteWinner(Long id, Long venueId);
}
