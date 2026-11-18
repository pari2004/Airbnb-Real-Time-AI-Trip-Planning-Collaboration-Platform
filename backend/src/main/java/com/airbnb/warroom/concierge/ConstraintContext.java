package com.airbnb.warroom.concierge;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;

/**
 * The fully-resolved set of hard constraints a suggestion request must satisfy (PRD 6.2). A null
 * field means that constraint is inactive for this request (e.g. no anchor -> no proximity filter).
 */
public record ConstraintContext(
        BigDecimal budgetCeiling,
        Set<String> dietaryTags,
        LocalTime requestedStart,
        LocalTime requestedEnd,
        Double anchorLat,
        Double anchorLng,
        Double radiusKm,
        int bufferMinutes,
        String categoryHint) {

    public boolean hasBudget() {
        return budgetCeiling != null;
    }

    public boolean hasDietary() {
        return dietaryTags != null && !dietaryTags.isEmpty();
    }

    public boolean hasTimeWindow() {
        return requestedStart != null && requestedEnd != null;
    }

    public boolean hasAnchor() {
        return anchorLat != null && anchorLng != null && radiusKm != null;
    }
}
