package com.airbnb.warroom.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Shared WebSocket envelope (PRD 7.3.4):
 * {@code { "event": "<event.name>", "tripId": "tr_44210", "payload": { ... }, "ts": 169... }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Envelope(String event, String tripId, Object payload, long ts) {

    public static Envelope of(String event, String tripId, Object payload) {
        return new Envelope(event, tripId, payload, System.currentTimeMillis());
    }
}
