package com.airbnb.warroom.analytics;

import com.airbnb.warroom.budget.BudgetDtos.BudgetSummary;

/** Session analytics payload (PRD Epic 3). */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record SessionAnalytics(
            String tripId,
            long planningDurationMinutes,
            int peakConcurrentPlanners,
            long aiQuestionsAsked,
            long conflictsFlagged,
            long votesResolved,
            long pinsAndMoves,
            long totalEvents,
            BudgetSummary finalBudget) {
    }
}
