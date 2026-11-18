package com.airbnb.warroom.trip;

import com.airbnb.warroom.common.ApiException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Central server-side RBAC gate for room-scoped actions (PRD 10.1). Every board/vote/chat/moderation
 * action resolves the caller's {@link RoomRole} here and asserts the required capability, so access
 * control is enforced on the server — never merely hidden in the UI.
 */
@Service
public class TripAccessService {

    private final TripParticipantRepository participants;

    public TripAccessService(TripParticipantRepository participants) {
        this.participants = participants;
    }

    public Mono<RoomRole> roleOf(Long tripId, Long userId) {
        return participants.find(tripId, userId)
                .map(p -> RoomRole.from(p.roomRole()))
                .switchIfEmpty(Mono.error(ApiException.forbidden("Not a participant of this trip")));
    }

    public Mono<RoomRole> requireMember(Long tripId, Long userId) {
        return roleOf(tripId, userId);
    }

    public Mono<RoomRole> requireCanPin(Long tripId, Long userId) {
        return roleOf(tripId, userId).flatMap(r -> r.canPin()
                ? Mono.just(r) : Mono.error(ApiException.forbidden("Requires Planner or Leader role")));
    }

    public Mono<RoomRole> requireCanVote(Long tripId, Long userId) {
        return roleOf(tripId, userId);
    }

    public Mono<RoomRole> requireLeader(Long tripId, Long userId) {
        return roleOf(tripId, userId).flatMap(r -> r.canModerate()
                ? Mono.just(r) : Mono.error(ApiException.forbidden("Requires Leader role")));
    }
}
