package com.airbnb.warroom.trip;

import java.time.Instant;

/** Per-room membership + role (PRD RBAC: viewer/planner/leader at the trip level). */
public record TripParticipant(Long tripId, Long userId, String roomRole, boolean muted, Instant joinedAt) {
}
