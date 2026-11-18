package com.airbnb.warroom.analytics;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Append-only structured event log (PRD 10.3). Analytics (PRD Epic 3) are reconciled against
 * this log, so every board-mutating action and AI request writes one row here.
 */
@Table("session_events")
public class SessionEvent {

    @Id
    private Long id;

    @Column("trip_id")
    private Long tripId;

    @Column("user_id")
    private Long userId;

    @Column("correlation_id")
    private String correlationId;

    @Column("event_type")
    private String eventType;

    private String payload;

    @Column("created_at")
    private Instant createdAt;

    public SessionEvent() {
    }

    public SessionEvent(Long tripId, Long userId, String correlationId, String eventType, String payload) {
        this.tripId = tripId;
        this.userId = userId;
        this.correlationId = correlationId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
