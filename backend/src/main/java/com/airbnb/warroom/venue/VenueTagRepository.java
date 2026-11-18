package com.airbnb.warroom.venue;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Access to the many-to-many {@code venue_dietary_tags} table. */
@Repository
public class VenueTagRepository {

    private final DatabaseClient db;

    public VenueTagRepository(DatabaseClient db) {
        this.db = db;
    }

    /** Returns rows of (venue_id, tag) for all venues in a destination, for in-memory grouping. */
    public Flux<Map<String, Object>> findTagsByDestination(String destination) {
        return db.sql("SELECT t.venue_id AS venue_id, t.tag AS tag FROM venue_dietary_tags t " +
                        "JOIN venue_metadata v ON v.id = t.venue_id WHERE v.destination = :d")
                .bind("d", destination)
                .map((row, meta) -> Map.<String, Object>of(
                        "venue_id", row.get("venue_id", Long.class),
                        "tag", row.get("tag", String.class)))
                .all();
    }

    public Flux<Map<String, Object>> findAllTags() {
        return db.sql("SELECT venue_id, tag FROM venue_dietary_tags")
                .map((row, meta) -> Map.<String, Object>of(
                        "venue_id", row.get("venue_id", Long.class),
                        "tag", row.get("tag", String.class)))
                .all();
    }

    public Flux<String> findTagsByVenue(Long venueId) {
        return db.sql("SELECT tag FROM venue_dietary_tags WHERE venue_id = :v ORDER BY tag")
                .bind("v", venueId)
                .map((row, meta) -> row.get("tag", String.class))
                .all();
    }

    public Mono<Void> addTag(Long venueId, String tag) {
        return db.sql("INSERT INTO venue_dietary_tags(venue_id, tag) VALUES (:v, :t) ON CONFLICT DO NOTHING")
                .bind("v", venueId).bind("t", tag)
                .then();
    }

    public Mono<Void> deleteByVenue(Long venueId) {
        return db.sql("DELETE FROM venue_dietary_tags WHERE venue_id = :v")
                .bind("v", venueId)
                .then();
    }
}
