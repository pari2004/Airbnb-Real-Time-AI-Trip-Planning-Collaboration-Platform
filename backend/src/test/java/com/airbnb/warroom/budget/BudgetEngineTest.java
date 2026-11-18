package com.airbnb.warroom.budget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** PRD KPI: budget math is a hard-accuracy requirement (100%). Splits must sum EXACTLY to the total. */
class BudgetEngineTest {

    private final BudgetEngine engine = new BudgetEngine();

    private BigDecimal sum(List<BigDecimal> xs) {
        return xs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    @DisplayName("Even split of a clean amount")
    void evenClean() {
        List<BigDecimal> shares = engine.evenSplit(new BigDecimal("120.00"), 4);
        assertEquals(4, shares.size());
        assertTrue(shares.stream().allMatch(s -> s.compareTo(new BigDecimal("30.00")) == 0));
        assertEquals(0, sum(shares).compareTo(new BigDecimal("120.00")));
    }

    @Test
    @DisplayName("Even split with an indivisible remainder still sums exactly")
    void evenRemainder() {
        List<BigDecimal> shares = engine.evenSplit(new BigDecimal("100.00"), 3);
        assertEquals(0, sum(shares).compareTo(new BigDecimal("100.00")));
        // Two people pay 33.34 and one pays 33.33 (or similar) — never a lost cent.
        assertTrue(shares.stream().allMatch(s -> s.compareTo(new BigDecimal("33.33")) >= 0
                && s.compareTo(new BigDecimal("33.34")) <= 0));
    }

    @Test
    @DisplayName("Weighted split sums exactly to the total")
    void weighted() {
        List<BigDecimal> shares = engine.weightedSplit(new BigDecimal("100.00"),
                List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("2")));
        assertEquals(0, sum(shares).compareTo(new BigDecimal("100.00")));
        // 1:1:2 of 100 -> 25,25,50
        assertEquals(0, shares.get(2).compareTo(new BigDecimal("50.00")));
    }

    @Test
    @DisplayName("Weighted split with awkward weights never drifts")
    void weightedAwkward() {
        List<BigDecimal> shares = engine.weightedSplit(new BigDecimal("99.99"),
                List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1")));
        assertEquals(0, sum(shares).compareTo(new BigDecimal("99.99")));
    }

    @Test
    @DisplayName("Running per-person totals accumulate correctly")
    void running() {
        List<BigDecimal> running = engine.runningPerPersonTotals(List.of(
                new BigDecimal("22.00"), new BigDecimal("15.00"), new BigDecimal("8.50")));
        assertEquals(0, running.get(0).compareTo(new BigDecimal("22.00")));
        assertEquals(0, running.get(1).compareTo(new BigDecimal("37.00")));
        assertEquals(0, running.get(2).compareTo(new BigDecimal("45.50")));
    }

    @Test
    @DisplayName("Even split rejects non-positive party sizes")
    void guard() {
        assertThrows(RuntimeException.class, () -> engine.evenSplit(new BigDecimal("10.00"), 0));
    }
}
