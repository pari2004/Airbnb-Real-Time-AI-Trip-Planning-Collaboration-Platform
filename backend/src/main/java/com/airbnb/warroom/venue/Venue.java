package com.airbnb.warroom.venue;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Immutable domain view of a venue with its dietary tags attached — the unit the AI Concierge
 * constraint matcher and RAG retriever reason over. Assembled from {@code venue_metadata} +
 * {@code venue_dietary_tags} by {@link VenueService}.
 */
public record Venue(
        Long id,
        String destination,
        String name,
        String category,
        BigDecimal pricePerPerson,
        LocalTime hoursOpen,
        LocalTime hoursClose,
        double lat,
        double lng,
        double rating,
        String description,
        List<String> dietaryTags) {
}
