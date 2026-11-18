package com.airbnb.warroom.auth.dto;

import com.airbnb.warroom.user.UserDto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Auth request/response payloads. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
            @NotBlank String displayName,
            @PositiveOrZero BigDecimal budgetCeiling,
            List<String> dietaryTags) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserDto user) {
    }
}
