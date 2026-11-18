package com.airbnb.warroom.trip;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Composite-key ({@code trip_id,user_id}) access for trip_participants via {@link DatabaseClient},
 * since R2DBC's {@code ReactiveCrudRepository} assumes a single {@code @Id}.
 */
@Repository
public class TripParticipantRepository {

    private final DatabaseClient db;

    public TripParticipantRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<TripParticipant> find(Long tripId, Long userId) {
        return db.sql("SELECT trip_id, user_id, room_role, muted, joined_at FROM trip_participants " +
                        "WHERE trip_id = :t AND user_id = :u")
                .bind("t", tripId).bind("u", userId)
                .map(TripParticipantRepository::map)
                .one();
    }

    public Flux<TripParticipant> findByTrip(Long tripId) {
        return db.sql("SELECT trip_id, user_id, room_role, muted, joined_at FROM trip_participants " +
                        "WHERE trip_id = :t ORDER BY joined_at")
                .bind("t", tripId)
                .map(TripParticipantRepository::map)
                .all();
    }

    /** Participant rows joined with display name, for building a {@code TripView}. */
    public Flux<TripDtos.ParticipantView> findViewsByTrip(Long tripId) {
        return db.sql("SELECT p.user_id AS user_id, u.display_name AS display_name, " +
                        "p.room_role AS room_role, p.muted AS muted " +
                        "FROM trip_participants p JOIN users u ON u.id = p.user_id " +
                        "WHERE p.trip_id = :t ORDER BY p.joined_at")
                .bind("t", tripId)
                .map((row, meta) -> new TripDtos.ParticipantView(
                        com.airbnb.warroom.common.PublicId.encode(
                                com.airbnb.warroom.common.PublicId.USER, row.get("user_id", Long.class)),
                        row.get("display_name", String.class),
                        row.get("room_role", String.class),
                        Boolean.TRUE.equals(row.get("muted", Boolean.class))))
                .all();
    }

    public Flux<Long> findTripIdsForUser(Long userId) {
        return db.sql("SELECT trip_id FROM trip_participants WHERE user_id = :u")
                .bind("u", userId)
                .map((row, meta) -> row.get("trip_id", Long.class))
                .all();
    }

    public Mono<Long> countByTrip(Long tripId) {
        return db.sql("SELECT count(*) AS c FROM trip_participants WHERE trip_id = :t")
                .bind("t", tripId)
                .map((row, meta) -> row.get("c", Long.class))
                .one();
    }

    /** Insert or update a participant's role (idempotent join). */
    public Mono<Void> upsert(Long tripId, Long userId, String roomRole) {
        return db.sql("INSERT INTO trip_participants(trip_id, user_id, room_role) VALUES (:t, :u, :r) " +
                        "ON CONFLICT (trip_id, user_id) DO UPDATE SET room_role = EXCLUDED.room_role")
                .bind("t", tripId).bind("u", userId).bind("r", roomRole)
                .then();
    }

    public Mono<Void> setMuted(Long tripId, Long userId, boolean muted) {
        return db.sql("UPDATE trip_participants SET muted = :m WHERE trip_id = :t AND user_id = :u")
                .bind("m", muted).bind("t", tripId).bind("u", userId)
                .then();
    }

    public Mono<Void> remove(Long tripId, Long userId) {
        return db.sql("DELETE FROM trip_participants WHERE trip_id = :t AND user_id = :u")
                .bind("t", tripId).bind("u", userId)
                .then();
    }

    private static TripParticipant map(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new TripParticipant(
                row.get("trip_id", Long.class),
                row.get("user_id", Long.class),
                row.get("room_role", String.class),
                Boolean.TRUE.equals(row.get("muted", Boolean.class)),
                row.get("joined_at", Instant.class));
    }
}
