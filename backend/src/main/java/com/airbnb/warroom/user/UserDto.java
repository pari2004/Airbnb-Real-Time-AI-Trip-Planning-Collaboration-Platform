package com.airbnb.warroom.user;

import com.airbnb.warroom.common.PublicId;

import java.math.BigDecimal;
import java.util.List;

/** Public representation of a user (never exposes the password hash). */
public record UserDto(String id, String email, String displayName, String role,
                      BigDecimal budgetCeiling, List<String> dietaryTags) {

    public static UserDto from(User u) {
        return new UserDto(
                PublicId.encode(PublicId.USER, u.getId()),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.getBudgetCeiling(),
                u.getDietaryTags() == null ? List.of() : List.of(u.getDietaryTags()));
    }
}
