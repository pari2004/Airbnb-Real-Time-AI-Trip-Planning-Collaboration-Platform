package com.airbnb.warroom.concierge;

import com.airbnb.warroom.common.GeoUtil;
import com.airbnb.warroom.venue.Venue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRD 6.2 acceptance: every suggestion the matcher returns must satisfy ALL active constraints.
 * The verification here is independent of the matcher implementation (recomputed from raw metadata)
 * so the "100% compliance" assertion is not a tautology.
 */
class ConstraintMatcherTest {

    private final ConstraintMatcher matcher = new ConstraintMatcher();

    private Venue venue(long id, String cat, double price, List<String> tags,
                        String open, String close, double lat, double lng) {
        return new Venue(id, "Lisbon, Portugal", "V" + id, cat, BigDecimal.valueOf(price),
                LocalTime.parse(open), LocalTime.parse(close), lat, lng, 4.5, "desc " + cat, tags);
    }

    private List<Venue> sampleVenues() {
        List<Venue> v = new ArrayList<>();
        v.add(venue(1, "dinner", 22, List.of("vegetarian"), "11:00", "22:00", 38.7139, -9.1394));
        v.add(venue(2, "dinner", 18, List.of("vegetarian", "vegan"), "12:00", "23:00", 38.7118, -9.1304));
        v.add(venue(3, "dinner", 120, List.of("gluten-free"), "19:00", "23:30", 38.7072, -9.1460));
        v.add(venue(4, "dinner", 28, List.of("vegan"), "18:00", "20:00", 38.7156, -9.1290));
        v.add(venue(5, "dinner", 16, List.of("halal", "vegetarian"), "11:30", "22:30", 38.7165, -9.1360));
        v.add(venue(6, "dinner", 24, List.of("vegetarian", "vegan", "halal", "gluten-free"),
                "10:00", "23:59", 38.7072, -9.1458));
        v.add(venue(7, "activity", 15, List.of(), "09:00", "21:00", 38.7139, -9.1335));
        return v;
    }

    private boolean independentlyValid(Venue v, ConstraintContext ctx) {
        if (ctx.hasBudget() && v.pricePerPerson().compareTo(ctx.budgetCeiling()) > 0) {
            return false;
        }
        if (ctx.hasDietary() && !v.dietaryTags().containsAll(ctx.dietaryTags())) {
            return false;
        }
        if (ctx.hasTimeWindow()) {
            LocalTime requiredOpenBy = ctx.requestedStart().minusMinutes(ctx.bufferMinutes());
            if (requiredOpenBy.isAfter(ctx.requestedStart())) {
                requiredOpenBy = LocalTime.MIN;
            }
            if (v.hoursOpen().isAfter(requiredOpenBy) || v.hoursClose().isBefore(ctx.requestedEnd())) {
                return false;
            }
        }
        if (ctx.hasAnchor()) {
            double d = GeoUtil.haversineKm(ctx.anchorLat(), ctx.anchorLng(), v.lat(), v.lng());
            if (d > ctx.radiusKm()) {
                return false;
            }
        }
        if (ctx.categoryHint() != null && !v.category().equalsIgnoreCase(ctx.categoryHint())) {
            return false;
        }
        return true;
    }

    @Test
    @DisplayName("Budget ceiling: over-price venues are rejected")
    void budgetCeiling() {
        ConstraintContext ctx = new ConstraintContext(BigDecimal.valueOf(25), Set.of(), null, null,
                null, null, null, 30, null);
        List<Venue> result = matcher.filter(sampleVenues(), ctx);
        assertTrue(result.stream().noneMatch(v -> v.pricePerPerson().compareTo(BigDecimal.valueOf(25)) > 0));
        assertTrue(result.stream().noneMatch(v -> v.id() == 3)); // $120 excluded
    }

    @Test
    @DisplayName("Dietary: a single missing tag disqualifies the venue")
    void dietaryAllMatch() {
        ConstraintContext ctx = new ConstraintContext(null, Set.of("vegetarian", "vegan"), null, null,
                null, null, null, 30, null);
        List<Venue> result = matcher.filter(sampleVenues(), ctx);
        assertTrue(result.stream().allMatch(v -> v.dietaryTags().containsAll(Set.of("vegetarian", "vegan"))));
        assertTrue(result.stream().noneMatch(v -> v.id() == 1)); // only vegetarian -> excluded
    }

    @Test
    @DisplayName("Operating hours must fully contain the slot including the buffer")
    void operatingHours() {
        // Slot 18:30-20:30 with 30m buffer -> venue must open by 18:00 and close by >= 20:30.
        ConstraintContext ctx = new ConstraintContext(null, Set.of(),
                LocalTime.of(18, 30), LocalTime.of(20, 30), null, null, null, 30, null);
        List<Venue> result = matcher.filter(sampleVenues(), ctx);
        // Venue 4 (18:00-20:00) closes too early -> excluded.
        assertTrue(result.stream().noneMatch(v -> v.id() == 4));
        assertTrue(result.stream().allMatch(v -> independentlyValid(v, ctx)));
    }

    @Test
    @DisplayName("Proximity: only venues within the radius of the anchor are kept")
    void proximity() {
        ConstraintContext ctx = new ConstraintContext(null, Set.of(), null, null,
                38.7139, -9.1394, 0.5, 30, null);
        List<Venue> result = matcher.filter(sampleVenues(), ctx);
        assertTrue(result.stream().allMatch(v ->
                GeoUtil.haversineKm(38.7139, -9.1394, v.lat(), v.lng()) <= 0.5));
    }

    @Test
    @DisplayName("100% constraint compliance across 25 randomized suggestion requests")
    void bulkComplianceAcrossManyRequests() {
        List<Venue> venues = sampleVenues();
        Random rnd = new Random(42);
        String[][] dietSets = {{}, {"vegetarian"}, {"vegan"}, {"vegetarian", "vegan"}, {"halal"}};

        for (int i = 0; i < 25; i++) {
            BigDecimal budget = BigDecimal.valueOf(10 + rnd.nextInt(120));
            Set<String> diet = Set.of(dietSets[rnd.nextInt(dietSets.length)]);
            boolean withTime = rnd.nextBoolean();
            LocalTime start = withTime ? LocalTime.of(11 + rnd.nextInt(8), 0) : null;
            LocalTime end = withTime ? start.plusHours(2) : null;
            boolean withAnchor = rnd.nextBoolean();
            Double lat = withAnchor ? 38.7139 : null;
            Double lng = withAnchor ? -9.1394 : null;
            Double radius = withAnchor ? 2.0 : null;

            ConstraintContext ctx = new ConstraintContext(budget, diet,
                    (end != null && end.isBefore(start)) ? null : start,
                    (end != null && end.isBefore(start)) ? null : end,
                    lat, lng, radius, 30, null);

            List<Venue> result = matcher.filter(venues, ctx);
            for (Venue v : result) {
                assertTrue(independentlyValid(v, ctx),
                        "Request " + i + " returned an invalid venue " + v.id());
            }
        }
    }
}
