package com.airbnb.warroom.dashboard;

import com.airbnb.warroom.user.UserDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Personal dashboard payloads (PRD Epic 3). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record CategoryCount(String category, long count) {
    }

    public record ProfileView(BigDecimal budgetCeiling, List<String> dietaryTags,
                              List<CategoryCount> favoredCategories) {
    }

    public record TripSummary(String tripId, String destination, String status, String roomRole,
                              LocalDate startDate, LocalDate endDate) {
    }

    public record DashboardView(UserDto user, ProfileView profile,
                                List<TripSummary> activeRooms, List<TripSummary> pastTrips) {
    }
}
