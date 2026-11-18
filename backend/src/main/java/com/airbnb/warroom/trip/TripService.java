package com.airbnb.warroom.trip;

import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.PublicId;
import com.airbnb.warroom.trip.TripDtos.CreateTripRequest;
import com.airbnb.warroom.trip.TripDtos.SettingsRequest;
import com.airbnb.warroom.trip.TripDtos.TripView;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;

/** Trip Room lifecycle: create, join, invite, settings, and moderation (PRD Epic 1). */
@Service
public class TripService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no ambiguous chars
    private final SecureRandom random = new SecureRandom();

    private final TripRepository trips;
    private final TripParticipantRepository participants;
    private final TripAccessService access;

    public TripService(TripRepository trips, TripParticipantRepository participants, TripAccessService access) {
        this.trips = trips;
        this.participants = participants;
        this.access = access;
    }

    public Mono<Trip> createTrip(Long leaderUserId, CreateTripRequest req) {
        if (!req.endDate().isAfter(req.startDate()) && !req.endDate().isEqual(req.startDate())) {
            return Mono.error(ApiException.badRequest("endDate must be on or after startDate"));
        }
        return generateUniqueInviteCode().flatMap(code -> {
            Trip t = new Trip();
            t.setLeaderUserId(leaderUserId);
            t.setDestination(req.destination());
            t.setStartDate(req.startDate());
            t.setEndDate(req.endDate());
            t.setTotalBudget(req.totalBudget());
            t.setInviteCode(code);
            t.setStatus("drafting");
            t.setMaxParticipants(req.maxParticipants() == null ? 50 : req.maxParticipants());
            t.setAiVerbosity("normal");
            return trips.save(t)
                    .flatMap(saved -> participants.upsert(saved.getId(), leaderUserId, RoomRole.LEADER.wire())
                            .thenReturn(saved));
        });
    }

    public Mono<TripView> getTripView(Long tripId, Long userId) {
        return access.requireMember(tripId, userId).then(buildView(tripId));
    }

    public Mono<TripView> joinByCode(Long userId, String inviteCode) {
        return trips.findByInviteCode(inviteCode.trim().toUpperCase())
                .switchIfEmpty(Mono.error(ApiException.notFound("Invite code")))
                .flatMap(trip -> participants.find(trip.getId(), userId)
                        .flatMap(existing -> buildView(trip.getId())) // already a member -> idempotent
                        .switchIfEmpty(Mono.defer(() -> participants.countByTrip(trip.getId())
                                .flatMap(count -> {
                                    if (count >= trip.getMaxParticipants()) {
                                        return Mono.error(ApiException.conflict("Trip Room is at capacity"));
                                    }
                                    return participants.upsert(trip.getId(), userId, RoomRole.PLANNER.wire())
                                            .then(buildView(trip.getId()));
                                }))));
    }

    public Mono<TripDtos.InviteView> invite(Long tripId, Long userId) {
        return access.requireLeader(tripId, userId)
                .then(trips.findById(tripId))
                .switchIfEmpty(Mono.error(ApiException.notFound("Trip")))
                .map(t -> new TripDtos.InviteView(t.getInviteCode(),
                        "/join?code=" + t.getInviteCode()));
    }

    public Mono<TripView> updateSettings(Long tripId, Long userId, SettingsRequest req) {
        return access.requireLeader(tripId, userId)
                .then(trips.findById(tripId))
                .switchIfEmpty(Mono.error(ApiException.notFound("Trip")))
                .flatMap(t -> {
                    if (req.maxParticipants() != null) {
                        t.setMaxParticipants(req.maxParticipants());
                    }
                    if (req.aiVerbosity() != null) {
                        t.setAiVerbosity(req.aiVerbosity());
                    }
                    if (req.totalBudget() != null) {
                        t.setTotalBudget(req.totalBudget());
                    }
                    if (req.status() != null) {
                        if (!java.util.Set.of("drafting", "live", "finalized").contains(req.status())) {
                            return Mono.error(ApiException.badRequest("Invalid status"));
                        }
                        t.setStatus(req.status());
                    }
                    return trips.save(t);
                })
                .then(buildView(tripId));
    }

    public Mono<TripView> assignRole(Long tripId, Long leaderId, String targetPublicUserId, String role) {
        Long targetUserId = PublicId.decode(targetPublicUserId);
        RoomRole newRole = RoomRole.from(role);
        if (newRole == RoomRole.LEADER) {
            return Mono.error(ApiException.badRequest("Use transfer-leader to assign the Leader role"));
        }
        return access.requireLeader(tripId, leaderId)
                .then(participants.find(tripId, targetUserId))
                .switchIfEmpty(Mono.error(ApiException.notFound("Participant")))
                .then(participants.upsert(tripId, targetUserId, newRole.wire()))
                .then(buildView(tripId));
    }

    public Mono<TripView> transferLeader(Long tripId, Long leaderId, String targetPublicUserId) {
        Long targetUserId = PublicId.decode(targetPublicUserId);
        return access.requireLeader(tripId, leaderId)
                .then(participants.find(tripId, targetUserId)
                        .switchIfEmpty(Mono.error(ApiException.notFound("Participant"))))
                .then(trips.findById(tripId))
                .flatMap(t -> {
                    t.setLeaderUserId(targetUserId);
                    return trips.save(t);
                })
                .then(participants.upsert(tripId, targetUserId, RoomRole.LEADER.wire()))
                .then(participants.upsert(tripId, leaderId, RoomRole.PLANNER.wire()))
                .then(buildView(tripId));
    }

    private Mono<TripView> buildView(Long tripId) {
        return trips.findById(tripId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Trip")))
                .flatMap(t -> participants.findViewsByTrip(tripId).collectList()
                        .map(parts -> new TripView(
                                PublicId.encode(PublicId.TRIP, t.getId()),
                                t.getDestination(), t.getStartDate(), t.getEndDate(), t.getTotalBudget(),
                                t.getInviteCode(), t.getStatus(),
                                PublicId.encode(PublicId.USER, t.getLeaderUserId()),
                                t.getMaxParticipants(), parts)));
    }

    private Mono<String> generateUniqueInviteCode() {
        String code = randomCode();
        return trips.existsByInviteCode(code)
                .flatMap(exists -> Boolean.TRUE.equals(exists) ? generateUniqueInviteCode() : Mono.just(code));
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        sb.append('-');
        for (int i = 0; i < 4; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    // Provided for reuse by the realtime gateway / analytics.
    public Mono<Trip> getTrip(Long tripId) {
        return trips.findById(tripId).switchIfEmpty(Mono.error(ApiException.notFound("Trip")));
    }
}
