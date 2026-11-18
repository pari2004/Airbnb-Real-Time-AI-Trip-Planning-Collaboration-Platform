package com.airbnb.warroom.trip;

import com.airbnb.warroom.common.PublicId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Trip API payloads (PRD 7.3.1). */
public final class TripDtos {

    private TripDtos() {
    }

    public record CreateTripRequest(
            @NotBlank String destination,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @Positive BigDecimal totalBudget,
            Integer maxParticipants) {
    }

    /** Matches PRD 7.3.1 response exactly. */
    public record CreateTripResponse(String tripId, String inviteCode, String status,
                                     String leaderUserId, Instant createdAt) {
        public static CreateTripResponse from(Trip t) {
            return new CreateTripResponse(
                    PublicId.encode(PublicId.TRIP, t.getId()),
                    t.getInviteCode(),
                    t.getStatus(),
                    PublicId.encode(PublicId.USER, t.getLeaderUserId()),
                    t.getCreatedAt());
        }
    }

    public record JoinRequest(@NotBlank String inviteCode) {
    }

    public record ParticipantView(String userId, String displayName, String roomRole, boolean muted) {
    }

    public record TripView(String tripId, String destination, LocalDate startDate, LocalDate endDate,
                           BigDecimal totalBudget, String inviteCode, String status, String leaderUserId,
                           int maxParticipants, List<ParticipantView> participants) {
    }

    public record SettingsRequest(Integer maxParticipants, String aiVerbosity,
                                  BigDecimal totalBudget, String status) {
    }

    public record AssignRoleRequest(@NotBlank String userId, @NotBlank String role) {
    }

    public record TransferLeaderRequest(@NotBlank String userId) {
    }

    public record InviteView(String inviteCode, String inviteLink) {
    }
}
