package com.airbnb.warroom.auth;

/** Authenticated caller identity carried in the reactive security context. */
public record AuthPrincipal(Long userId, String email, String role) {
}
