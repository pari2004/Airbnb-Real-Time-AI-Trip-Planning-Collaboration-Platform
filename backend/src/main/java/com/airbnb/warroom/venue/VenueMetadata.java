package com.airbnb.warroom.venue;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

/** R2DBC entity for {@code venue_metadata}. Dietary tags live in a separate table (venue_dietary_tags). */
@Table("venue_metadata")
public class VenueMetadata {

    @Id
    private Long id;

    private String destination;
    private String name;
    private String category;

    @Column("price_per_person")
    private BigDecimal pricePerPerson;

    @Column("hours_open")
    private LocalTime hoursOpen;

    @Column("hours_close")
    private LocalTime hoursClose;

    private double lat;
    private double lng;
    private BigDecimal rating;
    private String description;

    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getPricePerPerson() {
        return pricePerPerson;
    }

    public void setPricePerPerson(BigDecimal pricePerPerson) {
        this.pricePerPerson = pricePerPerson;
    }

    public LocalTime getHoursOpen() {
        return hoursOpen;
    }

    public void setHoursOpen(LocalTime hoursOpen) {
        this.hoursOpen = hoursOpen;
    }

    public LocalTime getHoursClose() {
        return hoursClose;
    }

    public void setHoursClose(LocalTime hoursClose) {
        this.hoursClose = hoursClose;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
