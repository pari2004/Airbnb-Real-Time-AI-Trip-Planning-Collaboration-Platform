package com.airbnb.warroom.board;

import com.airbnb.warroom.board.BoardDtos.MoveOutcome;
import com.airbnb.warroom.board.BoardDtos.MoveRequest;
import com.airbnb.warroom.board.BoardDtos.PinRequest;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.venue.VenueRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;

/**
 * Authoritative board state machine (PRD 6.1). Per-item lifecycle:
 * {@code drafting -> voting -> locked}. Concurrent moves on the same item are resolved by an
 * optimistic {@code position_version} check (PRD 7.2): the stale writer is rejected and handed the
 * current server state to reconcile, so no silent overwrite can occur.
 */
@Service
public class BoardService {

    private final ItineraryItemRepository items;
    private final VenueRepository venues;

    public BoardService(ItineraryItemRepository items, VenueRepository venues) {
        this.items = items;
        this.venues = venues;
    }

    public Flux<ItineraryItem> getBoard(Long tripId) {
        return items.findByTripIdOrderByDayIndexAscStartTimeAsc(tripId);
    }

    public Mono<ItineraryItem> getItem(Long itemId) {
        return items.findById(itemId).switchIfEmpty(Mono.error(ApiException.notFound("Itinerary item")));
    }

    public Mono<ItineraryItem> pin(Long tripId, Long userId, PinRequest req) {
        Long venueId = PublicId.decode(req.venueId());
        LocalTime start = parse(req.startTime());
        LocalTime end = parse(req.endTime());
        if (!end.isAfter(start)) {
            return Mono.error(ApiException.badRequest("endTime must be after startTime"));
        }
        return venues.findById(venueId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Venue")))
                .flatMap(v -> {
                    ItineraryItem item = new ItineraryItem();
                    item.setTripId(tripId);
                    item.setVenueId(venueId);
                    item.setDayIndex(req.dayIndex());
                    item.setStartTime(start);
                    item.setEndTime(end);
                    item.setLifecycleStage("drafting");
                    item.setPinnedByUserId(userId);
                    item.setPositionVersion(0);
                    return items.save(item);
                });
    }

    /**
     * Optimistic move. Applies only if {@code positionVersion} still matches the server's and the
     * item is not locked; otherwise returns the current authoritative state as a conflict.
     */
    public Mono<MoveOutcome> move(Long tripId, MoveRequest req) {
        Long itemId = PublicId.decode(req.itemId());
        LocalTime start = parse(req.startTime());
        LocalTime end = parse(req.endTime());
        if (!end.isAfter(start)) {
            return Mono.error(ApiException.badRequest("endTime must be after startTime"));
        }
        return items.findById(itemId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Itinerary item")))
                .flatMap(existing -> {
                    if (!existing.getTripId().equals(tripId)) {
                        return Mono.error(ApiException.badRequest("Item does not belong to this trip"));
                    }
                    return items.moveIfVersionMatches(itemId, req.dayIndex(), start, end, req.positionVersion())
                            .flatMap(updated -> {
                                if (updated == 1) {
                                    return items.findById(itemId).map(MoveOutcome::applied);
                                }
                                // 0 rows -> version mismatch or item locked. Return authoritative state.
                                return items.findById(itemId).map(current -> {
                                    String reason = "locked".equals(current.getLifecycleStage())
                                            ? "item_locked" : "version_mismatch";
                                    return MoveOutcome.conflict(current, reason);
                                });
                            });
                });
    }

    public Mono<ItineraryItem> lock(Long itemId) {
        return setStage(itemId, "locked");
    }

    /** Leader override of a finalized item (PRD 6.1 moderation). */
    public Mono<ItineraryItem> unlock(Long itemId) {
        return setStage(itemId, "drafting");
    }

    public Mono<ItineraryItem> setStage(Long itemId, String stage) {
        return items.setLifecycleStage(itemId, stage)
                .then(items.findById(itemId))
                .switchIfEmpty(Mono.error(ApiException.notFound("Itinerary item")));
    }

    public Mono<ItineraryItem> applyVoteWinner(Long itemId, Long venueId) {
        return items.applyVoteWinner(itemId, venueId)
                .then(items.findById(itemId))
                .switchIfEmpty(Mono.error(ApiException.notFound("Itinerary item")));
    }

    public Mono<Void> remove(Long tripId, Long itemId, Long userId, boolean isLeader) {
        return getItem(itemId).flatMap(item -> {
            if (!item.getTripId().equals(tripId)) {
                return Mono.error(ApiException.badRequest("Item does not belong to this trip"));
            }
            if ("locked".equals(item.getLifecycleStage()) && !isLeader) {
                return Mono.error(ApiException.forbidden("Locked items can only be removed by the Leader"));
            }
            if (!isLeader && !item.getPinnedByUserId().equals(userId)) {
                return Mono.error(ApiException.forbidden("Only the pinner or Leader can remove this item"));
            }
            return items.deleteById(itemId);
        });
    }

    private LocalTime parse(String s) {
        try {
            return LocalTime.parse(s);
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("Time must be HH:mm (got '" + s + "')");
        }
    }
}
