package com.airbnb.warroom.realtime;

import com.airbnb.warroom.common.PublicId;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fan-out hub: one multicast {@link Sinks.Many} per Trip Room, plus live presence tracking
 * (PRD 6.1, 9.4). The authoritative board state is persisted by R2DBC; this hub only carries the
 * live event stream and who is currently connected. Keyed by {@code tripId} so the architecture
 * never assumes a single room.
 */
@Component
public class RoomHub {

    private record Room(Sinks.Many<Envelope> sink, Map<Long, Integer> presence) {
    }

    private final Map<Long, Room> rooms = new ConcurrentHashMap<>();

    private Room room(Long tripId) {
        return rooms.computeIfAbsent(tripId, id -> new Room(
                Sinks.many().multicast().onBackpressureBuffer(),
                new ConcurrentHashMap<>()));
    }

    /** Live event stream for a room (one subscription per connected socket). */
    public Flux<Envelope> subscribe(Long tripId) {
        return room(tripId).sink().asFlux();
    }

    public void broadcast(Long tripId, String event, Object payload) {
        broadcast(tripId, Envelope.of(event, PublicId.encode(PublicId.TRIP, tripId), payload));
    }

    public void broadcast(Long tripId, Envelope envelope) {
        Room r = rooms.get(tripId);
        if (r != null) {
            r.sink().tryEmitNext(envelope);
        }
    }

    /** Registers a connection; returns the room's current distinct-participant count. */
    public int join(Long tripId, Long userId) {
        Map<Long, Integer> presence = room(tripId).presence();
        presence.merge(userId, 1, Integer::sum);
        return presence.size();
    }

    /** Deregisters a connection; returns the room's current distinct-participant count. */
    public int leave(Long tripId, Long userId) {
        Room r = rooms.get(tripId);
        if (r == null) {
            return 0;
        }
        r.presence().compute(userId, (k, v) -> (v == null || v <= 1) ? null : v - 1);
        return r.presence().size();
    }

    public boolean isConnected(Long tripId, Long userId) {
        Room r = rooms.get(tripId);
        return r != null && r.presence().containsKey(userId);
    }

    public Set<Long> connectedUsers(Long tripId) {
        Room r = rooms.get(tripId);
        return r == null ? Set.of() : Set.copyOf(r.presence().keySet());
    }

    public int presenceCount(Long tripId) {
        Room r = rooms.get(tripId);
        return r == null ? 0 : r.presence().size();
    }
}
