package com.airbnb.warroom.vote;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Table("vote_sessions")
public class VoteSession {

    @Id
    private Long id;

    @Column("trip_id")
    private Long tripId;

    @Column("item_id")
    private Long itemId;

    @Column("candidate_venue_ids")
    private Long[] candidateVenueIds;

    @Column("opened_by_user_id")
    private Long openedByUserId;

    @Column("opened_at")
    private Instant openedAt;

    @Column("closes_at")
    private Instant closesAt;

    private String status;

    @Column("winning_venue_id")
    private Long winningVenueId;

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

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public Long[] getCandidateVenueIds() {
        return candidateVenueIds;
    }

    public void setCandidateVenueIds(Long[] candidateVenueIds) {
        this.candidateVenueIds = candidateVenueIds;
    }

    public Long getOpenedByUserId() {
        return openedByUserId;
    }

    public void setOpenedByUserId(Long openedByUserId) {
        this.openedByUserId = openedByUserId;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(Instant closesAt) {
        this.closesAt = closesAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWinningVenueId() {
        return winningVenueId;
    }

    public void setWinningVenueId(Long winningVenueId) {
        this.winningVenueId = winningVenueId;
    }
}
