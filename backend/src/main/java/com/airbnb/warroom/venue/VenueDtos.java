package com.airbnb.warroom.venue;

import com.airbnb.warroom.common.PublicId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** Venue API payloads (shapes mirror PRD 7.3.2). */
public final class VenueDtos {

    private VenueDtos() {
    }

    public record Hours(@NotBlank String open, @NotBlank String close) {
    }

    public record Coordinates(@NotNull Double lat, @NotNull Double lng) {
    }

    /** One venue in an upload payload. */
    public record VenueInput(
            @NotBlank String name,
            @NotBlank String category,
            @NotNull BigDecimal pricePerPerson,
            List<String> dietaryTags,
            @NotNull @Valid Hours hours,
            @NotNull @Valid Coordinates coordinates,
            BigDecimal rating,
            String description) {
    }

    /** Admin dataset upload (PRD 6.3 / 7.3.2). */
    public record VenueUploadRequest(
            @NotBlank String destination,
            @NotEmpty @Valid List<VenueInput> venues) {
    }

    public record VenueView(String venueId, String name, String category, BigDecimal pricePerPerson,
                            List<String> dietaryTags, Hours hours, Coordinates coordinates,
                            double rating, String description) {

        public static VenueView from(Venue v) {
            return new VenueView(
                    PublicId.encode(PublicId.VENUE, v.id()),
                    v.name(), v.category(), v.pricePerPerson(), v.dietaryTags(),
                    new Hours(v.hoursOpen().toString(), v.hoursClose().toString()),
                    new Coordinates(v.lat(), v.lng()),
                    v.rating(), v.description());
        }
    }

    public record VenueCatalogResponse(String destination, List<VenueView> venues) {
    }

    public record UploadResult(String destination, int inserted, List<String> warnings) {
    }
}
