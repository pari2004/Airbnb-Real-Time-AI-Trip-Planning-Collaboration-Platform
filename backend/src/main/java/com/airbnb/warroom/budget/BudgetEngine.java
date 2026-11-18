package com.airbnb.warroom.budget;

import com.airbnb.warroom.common.ApiException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Exact group budget-splitting (PRD KPI: budget math is a hard-accuracy requirement, 100%). All
 * arithmetic is in integer cents so results are deterministic and every split sums EXACTLY to the
 * total — no floating-point drift, no lost or phantom cents.
 */
@Component
public class BudgetEngine {

    /** Even split of {@code total} across {@code people}; sums exactly to total (remainder cents spread fairly). */
    public List<BigDecimal> evenSplit(BigDecimal total, int people) {
        if (people <= 0) {
            throw ApiException.badRequest("people must be positive");
        }
        long cents = toCents(total);
        long base = cents / people;
        int remainder = (int) (cents - base * people); // 0..people-1 extra cents
        List<BigDecimal> shares = new ArrayList<>(people);
        for (int i = 0; i < people; i++) {
            long share = base + (i < remainder ? 1 : 0);
            shares.add(fromCents(share));
        }
        return shares;
    }

    /**
     * Weighted split of {@code total} by {@code weights}; sums exactly to total. Uses the
     * largest-remainder method so rounding leftovers go to the largest fractional shares.
     */
    public List<BigDecimal> weightedSplit(BigDecimal total, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            throw ApiException.badRequest("weights are required");
        }
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() <= 0) {
            throw ApiException.badRequest("weights must sum to a positive value");
        }
        long totalCents = toCents(total);

        // Exact ideal cents per share, then floor and distribute the remainder by largest fraction.
        long[] floors = new long[weights.size()];
        double[] fractions = new double[weights.size()];
        long allocated = 0;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal ideal = BigDecimal.valueOf(totalCents)
                    .multiply(weights.get(i))
                    .divide(weightSum, 10, RoundingMode.HALF_UP);
            long floor = ideal.setScale(0, RoundingMode.FLOOR).longValueExact();
            floors[i] = floor;
            fractions[i] = ideal.subtract(BigDecimal.valueOf(floor)).doubleValue();
            allocated += floor;
        }
        long leftover = totalCents - allocated;

        // Rank indices by descending fractional part; hand out the leftover cents one at a time.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < weights.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> fractions[i]).reversed());
        for (int k = 0; k < leftover; k++) {
            floors[order.get(k % order.size())]++;
        }

        List<BigDecimal> shares = new ArrayList<>(weights.size());
        for (long c : floors) {
            shares.add(fromCents(c));
        }
        return shares;
    }

    /** Running per-person cumulative totals over a sequence of per-person item prices. */
    public List<BigDecimal> runningPerPersonTotals(List<BigDecimal> itemPrices) {
        List<BigDecimal> running = new ArrayList<>(itemPrices.size());
        BigDecimal acc = BigDecimal.ZERO.setScale(2);
        for (BigDecimal p : itemPrices) {
            acc = acc.add(p.setScale(2, RoundingMode.HALF_UP));
            running.add(acc);
        }
        return running;
    }

    public BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private long toCents(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest("amount is required");
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
