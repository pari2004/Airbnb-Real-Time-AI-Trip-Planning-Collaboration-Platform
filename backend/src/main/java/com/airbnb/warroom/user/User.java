package com.airbnb.warroom.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** R2DBC entity for the {@code users} table. Global role governs Admin; per-room roles live in trip_participants. */
@Table("users")
public class User {

    @Id
    private Long id;

    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("display_name")
    private String displayName;

    private String role;

    @Column("budget_ceiling")
    private BigDecimal budgetCeiling;

    @Column("dietary_tags")
    private String[] dietaryTags;

    @Column("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public BigDecimal getBudgetCeiling() {
        return budgetCeiling;
    }

    public void setBudgetCeiling(BigDecimal budgetCeiling) {
        this.budgetCeiling = budgetCeiling;
    }

    public String[] getDietaryTags() {
        return dietaryTags;
    }

    public void setDietaryTags(String[] dietaryTags) {
        this.dietaryTags = dietaryTags;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
