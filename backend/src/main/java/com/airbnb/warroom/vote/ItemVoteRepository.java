package com.airbnb.warroom.vote;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/** Composite-key access for {@code item_votes} (one vote per user per session). */
@Repository
public class ItemVoteRepository {

    private final DatabaseClient db;

    public ItemVoteRepository(DatabaseClient db) {
        this.db = db;
    }

    /** Cast or change a vote (idempotent per user per session). */
    public Mono<Void> castVote(Long voteSessionId, Long userId, Long choiceVenueId) {
        return db.sql("INSERT INTO item_votes(vote_session_id, user_id, choice_venue_id) " +
                        "VALUES (:s, :u, :c) ON CONFLICT (vote_session_id, user_id) " +
                        "DO UPDATE SET choice_venue_id = EXCLUDED.choice_venue_id, cast_at = now()")
                .bind("s", voteSessionId).bind("u", userId).bind("c", choiceVenueId)
                .then();
    }

    /** Tally: choice_venue_id -> count, for a session. */
    public Flux<Map<String, Object>> tally(Long voteSessionId) {
        return db.sql("SELECT choice_venue_id AS vid, count(*) AS c FROM item_votes " +
                        "WHERE vote_session_id = :s GROUP BY choice_venue_id ORDER BY c DESC")
                .bind("s", voteSessionId)
                .map((row, meta) -> Map.<String, Object>of(
                        "venueId", row.get("vid", Long.class),
                        "count", row.get("c", Long.class)))
                .all();
    }
}
