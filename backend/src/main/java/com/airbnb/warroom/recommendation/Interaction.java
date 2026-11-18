package com.airbnb.warroom.recommendation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** Interaction event feeding the recommendation engine (PRD 7.2 interactions). */
@Table("interactions")
public class Interaction {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("trip_id")
    private Long tripId;

    private String destination;
    private String category;

    @Column("venue_id")
    private Long venueId;

    private boolean liked;

    @Column("co_traveled_with")
    private Long[] coTraveledWith;

    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public boolean isLiked() {
        return liked;
    }

    public void setLiked(boolean liked) {
        this.liked = liked;
    }

    public Long[] getCoTraveledWith() {
        return coTraveledWith;
    }

    public void setCoTraveledWith(Long[] coTraveledWith) {
        this.coTraveledWith = coTraveledWith;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
