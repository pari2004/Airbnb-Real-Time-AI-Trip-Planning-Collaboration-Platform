package com.airbnb.warroom.venue;

import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.common.GeoUtil;
import com.airbnb.warroom.venue.VenueDtos.VenueInput;
import com.airbnb.warroom.venue.VenueDtos.VenueUploadRequest;
import com.airbnb.warroom.venue.VenueDtos.UploadResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Venue catalog assembly, proximity search, and validated dataset upload (PRD 6.3, 7.3.2). */
@Service
public class VenueService {

    private final VenueRepository venueRepo;
    private final VenueTagRepository tagRepo;

    public VenueService(VenueRepository venueRepo, VenueTagRepository tagRepo) {
        this.venueRepo = venueRepo;
        this.tagRepo = tagRepo;
    }

    /** All venues for a destination, assembled with their dietary tags. */
    public Mono<List<Venue>> loadVenues(String destination) {
        Mono<List<VenueMetadata>> venues = venueRepo.findByDestination(destination).collectList();
        Mono<Map<Long, List<String>>> tagsByVenue = tagRepo.findTagsByDestination(destination)
                .collectMultimap(r -> (Long) r.get("venue_id"), r -> (String) r.get("tag"))
                .map(mm -> mm.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue()))));
        return Mono.zip(venues, tagsByVenue)
                .map(t -> t.getT1().stream()
                        .map(v -> toDomain(v, t.getT2().getOrDefault(v.getId(), List.of())))
                        .collect(Collectors.toList()));
    }

    /** All venues across every destination, assembled with tags (for model training). */
    public Mono<List<Venue>> loadAll() {
        Mono<List<VenueMetadata>> venues = venueRepo.findAll().collectList();
        Mono<Map<Long, List<String>>> tagsByVenue = tagRepo.findAllTags()
                .collectMultimap(r -> (Long) r.get("venue_id"), r -> (String) r.get("tag"))
                .map(mm -> mm.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> new ArrayList<>(e.getValue()))));
        return Mono.zip(venues, tagsByVenue)
                .map(t -> t.getT1().stream()
                        .map(v -> toDomain(v, t.getT2().getOrDefault(v.getId(), List.of())))
                        .collect(Collectors.toList()));
    }

    public Mono<Venue> loadVenue(Long venueId) {
        return venueRepo.findById(venueId)
                .switchIfEmpty(Mono.error(ApiException.notFound("Venue")))
                .flatMap(v -> tagRepo.findTagsByVenue(venueId).collectList()
                        .map(tags -> toDomain(v, tags)));
    }

    /** Venues within {@code radiusKm} of an anchor point (or all, if no anchor supplied). */
    public Mono<List<Venue>> findNear(String destination, Double lat, Double lng, Double radiusKm) {
        return loadVenues(destination).map(all -> {
            if (lat == null || lng == null || radiusKm == null) {
                return all;
            }
            return all.stream()
                    .filter(v -> GeoUtil.haversineKm(lat, lng, v.lat(), v.lng()) <= radiusKm)
                    .collect(Collectors.toList());
        });
    }

    /**
     * Validates and inserts a destination dataset. Rejects the whole upload on the first hard
     * validation failure (price, coords, hours, unknown dietary tag) with a clear message.
     */
    public Mono<UploadResult> uploadDataset(VenueUploadRequest req) {
        List<String> warnings = new ArrayList<>();
        // Validate everything up front so a bad row never half-inserts.
        for (int i = 0; i < req.venues().size(); i++) {
            validate(req.venues().get(i), i, warnings);
        }
        return Flux.fromIterable(req.venues())
                .concatMap(input -> insertVenue(req.destination(), input))
                .then(Mono.fromSupplier(() -> new UploadResult(req.destination(), req.venues().size(), warnings)));
    }

    private Mono<Void> insertVenue(String destination, VenueInput in) {
        VenueMetadata v = new VenueMetadata();
        v.setDestination(destination);
        v.setName(in.name());
        v.setCategory(in.category());
        v.setPricePerPerson(in.pricePerPerson());
        v.setHoursOpen(LocalTime.parse(in.hours().open()));
        v.setHoursClose(LocalTime.parse(in.hours().close()));
        v.setLat(in.coordinates().lat());
        v.setLng(in.coordinates().lng());
        v.setRating(in.rating() == null ? new BigDecimal("4.00") : in.rating());
        v.setDescription(in.description() == null ? "" : in.description());
        List<String> tags = in.dietaryTags() == null ? List.of()
                : in.dietaryTags().stream().map(String::toLowerCase).distinct().toList();
        return venueRepo.save(v)
                .flatMap(saved -> Flux.fromIterable(tags)
                        .concatMap(tag -> tagRepo.addTag(saved.getId(), tag))
                        .then());
    }

    private void validate(VenueInput in, int index, List<String> warnings) {
        String where = "venues[" + index + "] (" + in.name() + "): ";
        if (in.pricePerPerson() == null || in.pricePerPerson().signum() < 0) {
            throw ApiException.badRequest(where + "pricePerPerson must be a non-negative number");
        }
        Double lat = in.coordinates().lat();
        Double lng = in.coordinates().lng();
        if (lat == null || lat < -90 || lat > 90) {
            throw ApiException.badRequest(where + "lat must be between -90 and 90");
        }
        if (lng == null || lng < -180 || lng > 180) {
            throw ApiException.badRequest(where + "lng must be between -180 and 180");
        }
        LocalTime open;
        LocalTime close;
        try {
            open = LocalTime.parse(in.hours().open());
            close = LocalTime.parse(in.hours().close());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest(where + "hours must be HH:mm (24-hour)");
        }
        if (!close.isAfter(open)) {
            throw ApiException.badRequest(where + "hours.close must be after hours.open");
        }
        if (in.dietaryTags() != null) {
            for (String tag : in.dietaryTags()) {
                if (!DietaryVocabulary.isValid(tag)) {
                    throw ApiException.badRequest(where + "unknown dietary tag '" + tag
                            + "'. Allowed: " + DietaryVocabulary.TAGS);
                }
            }
        }
        if (in.rating() != null && (in.rating().signum() < 0 || in.rating().compareTo(new BigDecimal("5")) > 0)) {
            warnings.add(where + "rating outside 0-5 was clamped on read");
        }
    }

    private Venue toDomain(VenueMetadata v, List<String> tags) {
        return new Venue(
                v.getId(), v.getDestination(), v.getName(), v.getCategory(), v.getPricePerPerson(),
                v.getHoursOpen(), v.getHoursClose(), v.getLat(), v.getLng(),
                v.getRating() == null ? 4.0 : v.getRating().doubleValue(),
                v.getDescription() == null ? "" : v.getDescription(),
                tags);
    }
}
