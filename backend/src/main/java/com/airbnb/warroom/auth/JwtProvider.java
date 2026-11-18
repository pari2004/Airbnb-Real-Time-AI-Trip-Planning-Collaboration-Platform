package com.airbnb.warroom.auth;

import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.config.AppProperties;
import com.airbnb.warroom.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates stateless JWT access tokens (PRD 10.1). The same token is accepted by the
 * REST layer and the WebSocket handshake. Refresh tokens are opaque (handled by {@link AuthService}).
 */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTtlSeconds;
    private final String issuer;

    public JwtProvider(AppProperties props) {
        byte[] secretBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (set JWT_SECRET env var)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlSeconds = props.jwt().accessTokenTtlSeconds();
        this.issuer = props.jwt().issuer();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    /** Parses and validates a token, returning the principal. Throws 401 on any failure. */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    claims.get("role", String.class));
        } catch (ExpiredJwtException e) {
            throw ApiException.unauthorized("Access token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Invalid access token");
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }
}
