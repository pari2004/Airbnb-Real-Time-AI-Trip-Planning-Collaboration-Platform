package com.airbnb.warroom.concierge;

import com.airbnb.warroom.board.ItineraryItem;
import com.airbnb.warroom.concierge.ConciergeDtos.ConflictView;
import com.airbnb.warroom.venue.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** PRD 6.2: schedule-overlap and budget-breach detection. */
class ConflictDetectorTest {

    private final ConflictDetector detector = new ConflictDetector();

    private ItineraryItem item(long id, long venueId, int day, String start, String end) {
        ItineraryItem it = new ItineraryItem();
        it.setId(id);
        it.setTripId(1L);
        it.setVenueId(venueId);
        it.setDayIndex(day);
        it.setStartTime(LocalTime.parse(start));
        it.setEndTime(LocalTime.parse(end));
        it.setLifecycleStage("drafting");
        it.setPinnedByUserId(1L);
        it.setPositionVersion(0);
        return it;
    }

    private Venue venue(long id, double price) {
        return new Venue(id, "Lisbon, Portugal", "V" + id, "dinner", BigDecimal.valueOf(price),
                LocalTime.of(10, 0), LocalTime.of(23, 0), 38.7, -9.1, 4.5, "", List.of());
    }

    @Test
    @DisplayName("Overlapping items on the same day are flagged")
    void overlapSameDay() {
        List<ItineraryItem> items = List.of(
                item(1, 10, 0, "17:00", "19:00"),
                item(2, 11, 0, "18:00", "20:00"));
        List<ConflictView> conflicts = detector.scheduleOverlaps(items);
        assertEquals(1, conflicts.size());
        assertEquals("overlap", conflicts.get(0).conflictType());
    }

    @Test
    @DisplayName("Same times on different days do not conflict")
    void noOverlapDifferentDay() {
        List<ItineraryItem> items = List.of(
                item(1, 10, 0, "17:00", "19:00"),
                item(2, 11, 1, "17:00", "19:00"));
        assertTrue(detector.scheduleOverlaps(items).isEmpty());
    }

    @Test
    @DisplayName("Adjacent (touching) items do not overlap")
    void adjacentNoOverlap() {
        List<ItineraryItem> items = List.of(
                item(1, 10, 0, "17:00", "18:00"),
                item(2, 11, 0, "18:00", "19:00"));
        assertTrue(detector.scheduleOverlaps(items).isEmpty());
    }

    @Test
    @DisplayName("Budget breach flagged when per-person total exceeds the ceiling")
    void budgetBreach() {
        List<ItineraryItem> items = List.of(item(1, 10, 0, "12:00", "13:00"),
                item(2, 11, 1, "12:00", "13:00"));
        Map<Long, Venue> venues = Map.of(10L, venue(10, 30), 11L, venue(11, 25));
        ConflictView breach = detector.budgetBreach(items, venues, new BigDecimal("40"));
        assertNotNull(breach);
        assertEquals("budgetBreach", breach.conflictType());
    }

    @Test
    @DisplayName("No budget breach when under ceiling")
    void underBudget() {
        List<ItineraryItem> items = List.of(item(1, 10, 0, "12:00", "13:00"));
        Map<Long, Venue> venues = Map.of(10L, venue(10, 30));
        assertNull(detector.budgetBreach(items, venues, new BigDecimal("40")));
    }
}
