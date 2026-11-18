package com.airbnb.warroom.concierge;

import com.airbnb.warroom.common.GeoUtil;
import com.airbnb.warroom.venue.Venue;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic hard-constraint filter (PRD 6.2). A venue is valid only if it satisfies EVERY active
 * constraint — budget ceiling, dietary compatibility (all declared tags present), operating hours
 * (fully contains the slot incl. buffer), and proximity (within radius of the anchor). This is the
 * component the "100% constraint compliance" acceptance test asserts against.
 */
@Component
public class ConstraintMatcher {

    /** Full filter: returns only venues that pass all active constraints. */
    public List<Venue> filter(List<Venue> venues, ConstraintContext ctx) {
        return venues.stream().filter(v -> isValid(v, ctx)).collect(Collectors.toList());
    }

    public boolean isValid(Venue v, ConstraintContext ctx) {
        return satisfiesBudget(v, ctx)
                && satisfiesDietary(v, ctx)
                && satisfiesHours(v, ctx)
                && satisfiesProximity(v, ctx)
                && satisfiesCategory(v, ctx);
    }

    /** Budget: price-per-person must not exceed the active ceiling. */
    public boolean satisfiesBudget(Venue v, ConstraintContext ctx) {
        if (!ctx.hasBudget()) {
            return true;
        }
        return v.pricePerPerson().compareTo(ctx.budgetCeiling()) <= 0;
    }

    /** Dietary: the venue must carry a matching tag for EVERY declared constraint (one miss disqualifies). */
    public boolean satisfiesDietary(Venue v, ConstraintContext ctx) {
        if (!ctx.hasDietary()) {
            return true;
        }
        List<String> venueTags = v.dietaryTags().stream().map(String::toLowerCase).toList();
        return ctx.dietaryTags().stream().map(String::toLowerCase).allMatch(venueTags::contains);
    }

    /**
     * Operating hours: the venue's open/close window must fully contain the requested slot, with the
     * configured travel buffer applied to the arrival edge (PRD 6.2).
     */
    public boolean satisfiesHours(Venue v, ConstraintContext ctx) {
        if (!ctx.hasTimeWindow()) {
            return true;
        }
        LocalTime requiredOpenBy = ctx.requestedStart().minusMinutes(ctx.bufferMinutes());
        // Guard against wrap below midnight: if subtraction underflows the day, clamp to 00:00.
        if (requiredOpenBy.isAfter(ctx.requestedStart())) {
            requiredOpenBy = LocalTime.MIN;
        }
        return !v.hoursOpen().isAfter(requiredOpenBy) && !v.hoursClose().isBefore(ctx.requestedEnd());
    }

    /** Proximity: within the configured radius of the anchor event's coordinates. */
    public boolean satisfiesProximity(Venue v, ConstraintContext ctx) {
        if (!ctx.hasAnchor()) {
            return true;
        }
        double dist = GeoUtil.haversineKm(ctx.anchorLat(), ctx.anchorLng(), v.lat(), v.lng());
        return dist <= ctx.radiusKm();
    }

    /** Relevance filter (not one of the four hard constraints): honour an explicit category ask. */
    public boolean satisfiesCategory(Venue v, ConstraintContext ctx) {
        if (ctx.categoryHint() == null) {
            return true;
        }
        return v.category().equalsIgnoreCase(ctx.categoryHint());
    }

    public Double distanceKm(Venue v, ConstraintContext ctx) {
        if (!ctx.hasAnchor()) {
            return null;
        }
        return GeoUtil.haversineKm(ctx.anchorLat(), ctx.anchorLng(), v.lat(), v.lng());
    }
}
