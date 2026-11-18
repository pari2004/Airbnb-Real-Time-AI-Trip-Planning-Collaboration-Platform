package com.airbnb.warroom.budget;

import java.math.BigDecimal;
import java.util.List;

/** Budget summary payloads (PRD Epic 1 budget panel / KPI: 100% accurate). */
public final class BudgetDtos {

    private BudgetDtos() {
    }

    public record LineItem(String itemId, String venueName, BigDecimal pricePerPerson, BigDecimal runningPerPerson) {
    }

    public record PerPersonShare(String userId, String displayName, BigDecimal share) {
    }

    public record BudgetSummary(
            BigDecimal totalBudget,
            int participantCount,
            BigDecimal perPersonPlanned,
            BigDecimal groupPlanned,
            BigDecimal remaining,
            boolean overBudget,
            List<LineItem> lineItems,
            List<PerPersonShare> evenSplit) {
    }
}
