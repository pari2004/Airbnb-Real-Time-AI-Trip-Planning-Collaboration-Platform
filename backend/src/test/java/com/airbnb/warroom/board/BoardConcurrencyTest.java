package com.airbnb.warroom.board;

import com.airbnb.warroom.board.BoardDtos.MoveRequest;
import com.airbnb.warroom.venue.VenueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PRD 6.1 acceptance: two simultaneous moves on the same item never silently overwrite — the stale
 * writer is rejected (version mismatch) and handed the current authoritative state to reconcile.
 */
class BoardConcurrencyTest {

    private ItineraryItem itemAt(int version, String stage) {
        ItineraryItem it = new ItineraryItem();
        it.setId(5L);
        it.setTripId(1L);
        it.setVenueId(10L);
        it.setDayIndex(0);
        it.setStartTime(LocalTime.of(10, 0));
        it.setEndTime(LocalTime.of(12, 0));
        it.setLifecycleStage(stage);
        it.setPinnedByUserId(1L);
        it.setPositionVersion(version);
        return it;
    }

    @Test
    @DisplayName("Winning move applies and bumps the version")
    void winnerApplies() {
        ItineraryItemRepository items = mock(ItineraryItemRepository.class);
        VenueRepository venues = mock(VenueRepository.class);
        when(items.findById(5L)).thenReturn(Mono.just(itemAt(1, "drafting")));
        when(items.moveIfVersionMatches(eq(5L), eq(1), any(), any(), eq(0))).thenReturn(Mono.just(1));

        BoardService board = new BoardService(items, venues);
        MoveRequest req = new MoveRequest("it_5", 1, "10:00", "12:00", 0);

        StepVerifier.create(board.move(1L, req))
                .assertNext(outcome -> {
                    org.junit.jupiter.api.Assertions.assertTrue(outcome.applied());
                    org.junit.jupiter.api.Assertions.assertEquals(1, outcome.item().positionVersion());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Losing move is rejected with the current state and a version_mismatch reason")
    void loserGetsConflict() {
        ItineraryItemRepository items = mock(ItineraryItemRepository.class);
        VenueRepository venues = mock(VenueRepository.class);
        // Current server version is already 1; the stale client still holds version 0.
        when(items.findById(5L)).thenReturn(Mono.just(itemAt(1, "drafting")));
        when(items.moveIfVersionMatches(eq(5L), eq(2), any(), any(), eq(0))).thenReturn(Mono.just(0));

        BoardService board = new BoardService(items, venues);
        MoveRequest req = new MoveRequest("it_5", 2, "13:00", "15:00", 0);

        StepVerifier.create(board.move(1L, req))
                .assertNext(outcome -> {
                    org.junit.jupiter.api.Assertions.assertFalse(outcome.applied());
                    org.junit.jupiter.api.Assertions.assertEquals("version_mismatch", outcome.conflictReason());
                    org.junit.jupiter.api.Assertions.assertEquals(1, outcome.item().positionVersion());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Move against a locked item is rejected as item_locked")
    void lockedItemRejected() {
        ItineraryItemRepository items = mock(ItineraryItemRepository.class);
        VenueRepository venues = mock(VenueRepository.class);
        when(items.findById(5L)).thenReturn(Mono.just(itemAt(3, "locked")));
        when(items.moveIfVersionMatches(eq(5L), eq(1), any(), any(), eq(3))).thenReturn(Mono.just(0));

        BoardService board = new BoardService(items, venues);
        MoveRequest req = new MoveRequest("it_5", 1, "10:00", "12:00", 3);

        StepVerifier.create(board.move(1L, req))
                .assertNext(outcome -> {
                    org.junit.jupiter.api.Assertions.assertFalse(outcome.applied());
                    org.junit.jupiter.api.Assertions.assertEquals("item_locked", outcome.conflictReason());
                })
                .verifyComplete();
    }
}
