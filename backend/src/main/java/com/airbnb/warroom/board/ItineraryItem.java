package com.airbnb.warroom.board;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalTime;

/** Board card. {@code positionVersion} is the optimistic-concurrency token (PRD 7.2). */
@Table("itinerary_items")
public class ItineraryItem {

    @Id
    private Long id;

    @Column("trip_id")
    private Long tripId;

    @Column("venue_id")
    private Long venueId;

    @Column("day_index")
    private int dayIndex;

    @Column("start_time")
    private LocalTime startTime;

    @Column("end_time")
    private LocalTime endTime;

    @Column("lifecycle_stage")
    private String lifecycleStage;

    @Column("pinned_by_user_id")
    private Long pinnedByUserId;

    @Column("position_version")
    private int positionVersion;

    @Column("created_at")
    private Instant createdAt;

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

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public int getDayIndex() {
        return dayIndex;
    }

    public void setDayIndex(int dayIndex) {
        this.dayIndex = dayIndex;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public String getLifecycleStage() {
        return lifecycleStage;
    }

    public void setLifecycleStage(String lifecycleStage) {
        this.lifecycleStage = lifecycleStage;
    }

    public Long getPinnedByUserId() {
        return pinnedByUserId;
    }

    public void setPinnedByUserId(Long pinnedByUserId) {
        this.pinnedByUserId = pinnedByUserId;
    }

    public int getPositionVersion() {
        return positionVersion;
    }

    public void setPositionVersion(int positionVersion) {
        this.positionVersion = positionVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
