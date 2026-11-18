package com.airbnb.warroom.board;

import com.airbnb.warroom.common.PublicId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalTime;
import java.util.List;

/** Board API / WebSocket payloads (PRD 6.1, 7.3.4). */
public final class BoardDtos {

    private BoardDtos() {
    }

    public record PinRequest(
            @NotNull String venueId,
            @NotNull @PositiveOrZero Integer dayIndex,
            @NotNull String startTime,
            @NotNull String endTime) {
    }

    public record MoveRequest(
            @NotNull String itemId,
            @NotNull @PositiveOrZero Integer dayIndex,
            @NotNull String startTime,
            @NotNull String endTime,
            @NotNull Integer positionVersion) {
    }

    public record ItemView(String itemId, String venueId, int dayIndex, String startTime, String endTime,
                           String lifecycleStage, int positionVersion, String pinnedByUserId) {

        public static ItemView from(ItineraryItem i) {
            return new ItemView(
                    PublicId.encode(PublicId.ITEM, i.getId()),
                    PublicId.encode(PublicId.VENUE, i.getVenueId()),
                    i.getDayIndex(),
                    i.getStartTime().toString(),
                    i.getEndTime().toString(),
                    i.getLifecycleStage(),
                    i.getPositionVersion(),
                    PublicId.encode(PublicId.USER, i.getPinnedByUserId()));
        }
    }

    public record BoardSnapshot(String tripId, List<ItemView> items) {
    }

    /** Result of a move: either applied, or rejected with the authoritative current state to reconcile. */
    public record MoveOutcome(boolean applied, ItemView item, String conflictReason) {
        public static MoveOutcome applied(ItineraryItem i) {
            return new MoveOutcome(true, ItemView.from(i), null);
        }

        public static MoveOutcome conflict(ItineraryItem current, String reason) {
            return new MoveOutcome(false, ItemView.from(current), reason);
        }
    }

    static LocalTime parseTime(String s) {
        return LocalTime.parse(s);
    }
}
