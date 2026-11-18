package com.airbnb.warroom.concierge;

import com.airbnb.warroom.auth.SecurityUtil;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.concierge.ConciergeDtos.AiAnswer;
import com.airbnb.warroom.concierge.ConciergeDtos.AskRequest;
import com.airbnb.warroom.realtime.Events;
import com.airbnb.warroom.realtime.RoomHub;
import com.airbnb.warroom.trip.TripAccessService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/** PRD 7.3.3 — POST /api/v1/trips/{id}/ai/ask. */
@RestController
@RequestMapping("/api/v1/trips")
public class ConciergeController {

    private final ConciergeService concierge;
    private final TripAccessService access;
    private final RoomHub roomHub;

    public ConciergeController(ConciergeService concierge, TripAccessService access, RoomHub roomHub) {
        this.concierge = concierge;
        this.access = access;
        this.roomHub = roomHub;
    }

    @PostMapping("/{id}/ai/ask")
    public Mono<AiAnswer> ask(@PathVariable String id, @Valid @RequestBody AskRequest req) {
        Long tripId = PublicId.decode(id);
        return SecurityUtil.currentUserId()
                .flatMap(uid -> access.requireMember(tripId, uid)
                        .then(concierge.ask(tripId, uid, req))
                        .doOnNext(ans -> broadcast(tripId, uid, ans)));
    }

    private void broadcast(Long tripId, Long userId, AiAnswer ans) {
        roomHub.broadcast(tripId, Events.AI_SUGGESTION, Map.of(
                "userId", PublicId.encode(PublicId.USER, userId),
                "answer", ans.answer(),
                "suggestedVenueId", ans.suggestedVenueId() == null ? "" : ans.suggestedVenueId(),
                "sourceEvents", ans.sourceEvents(),
                "grounded", ans.grounded()));
    }
}
