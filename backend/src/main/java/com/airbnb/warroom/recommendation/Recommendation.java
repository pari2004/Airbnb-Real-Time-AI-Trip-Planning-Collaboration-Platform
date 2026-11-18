package com.airbnb.warroom.recommendation;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** Materialized output of the offline hybrid recommender (PRD Epic 3). */
@Table("recommendations")
public class Recommendation {

    @Id
    private Long id;

    @Column("user_id")
    private Long userId;

    @Column("venue_id")
    private Long venueId;

    private String destination;
    private BigDecimal score;
    private int rank;

    @Column("generated_at")
    private Instant generatedAt;

    public Recommendation() {
    }

    public Recommendation(Long userId, Long venueId, String destination, BigDecimal score, int rank) {
        this.userId = userId;
        this.venueId = venueId;
        this.destination = destination;
        this.score = score;
        this.rank = rank;
    }

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

    public Long getVenueId() {
        return venueId;
    }

    public void setVenueId(Long venueId) {
        this.venueId = venueId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
