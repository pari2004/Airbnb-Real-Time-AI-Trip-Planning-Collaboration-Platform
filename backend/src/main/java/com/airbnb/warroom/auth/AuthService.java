package com.airbnb.warroom.auth;

import com.airbnb.warroom.auth.dto.AuthDtos.AuthResponse;
import com.airbnb.warroom.auth.dto.AuthDtos.LoginRequest;
import com.airbnb.warroom.auth.dto.AuthDtos.RegisterRequest;
import com.airbnb.warroom.common.ApiException;
import com.airbnb.warroom.config.AppProperties;
import com.airbnb.warroom.user.User;
import com.airbnb.warroom.user.UserDto;
import com.airbnb.warroom.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Registration, login, and the refresh-token rotation flow (PRD 10.1). */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final long refreshTtlSeconds;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtProvider jwtProvider, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTtlSeconds = props.jwt().refreshTokenTtlSeconds();
    }

    public Mono<AuthResponse> register(RegisterRequest req) {
        String email = req.email().toLowerCase().trim();
        return users.existsByEmail(email)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists)) {
                        return Mono.error(ApiException.conflict("Email already registered"));
                    }
                    return hash(req.password()).flatMap(hash -> {
                        User u = new User();
                        u.setEmail(email);
                        u.setPasswordHash(hash);
                        u.setDisplayName(req.displayName());
                        u.setRole("planner"); // global default; room role assigned per trip, admin is seeded
                        u.setBudgetCeiling(req.budgetCeiling());
                        u.setDietaryTags(req.dietaryTags() == null
                                ? new String[0] : req.dietaryTags().toArray(new String[0]));
                        return users.save(u);
                    });
                })
                .flatMap(this::issueTokens);
    }

    public Mono<AuthResponse> login(LoginRequest req) {
        String email = req.email().toLowerCase().trim();
        return users.findByEmail(email)
                .switchIfEmpty(Mono.error(ApiException.unauthorized("Invalid credentials")))
                .flatMap(u -> matches(req.password(), u.getPasswordHash())
                        .flatMap(ok -> ok ? issueTokens(u)
                                : Mono.error(ApiException.unauthorized("Invalid credentials"))));
    }

    public Mono<AuthResponse> refresh(String rawRefreshToken) {
        String tokenHash = sha256(rawRefreshToken);
        return refreshTokens.findByTokenHash(tokenHash)
                .switchIfEmpty(Mono.error(ApiException.unauthorized("Invalid refresh token")))
                .flatMap(rt -> {
                    if (rt.isRevoked() || rt.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(ApiException.unauthorized("Refresh token expired or revoked"));
                    }
                    rt.setRevoked(true); // rotate: single-use refresh tokens
                    return refreshTokens.save(rt)
                            .then(users.findById(rt.getUserId()))
                            .switchIfEmpty(Mono.error(ApiException.unauthorized("User no longer exists")))
                            .flatMap(this::issueTokens);
                });
    }

    public Mono<Void> logout(Long userId) {
        return refreshTokens.deleteByUserId(userId);
    }

    // ---- helpers ----------------------------------------------------------

    private Mono<AuthResponse> issueTokens(User user) {
        String accessToken = jwtProvider.generateAccessToken(user);
        String rawRefresh = randomToken();
        RefreshToken rt = new RefreshToken();
        rt.setUserId(user.getId());
        rt.setTokenHash(sha256(rawRefresh));
        rt.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds));
        rt.setRevoked(false);
        return refreshTokens.save(rt)
                .thenReturn(new AuthResponse(accessToken, rawRefresh,
                        jwtProvider.getAccessTtlSeconds(), UserDto.from(user)));
    }

    private Mono<String> hash(String raw) {
        return Mono.fromCallable(() -> passwordEncoder.encode(raw)).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Boolean> matches(String raw, String hash) {
        return Mono.fromCallable(() -> passwordEncoder.matches(raw, hash)).subscribeOn(Schedulers.boundedElastic());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Exposed for the seeder to create the demo admin without going through registration. */
    public Mono<User> ensureUser(String email, String rawPassword, String displayName, String role,
                                 java.math.BigDecimal budgetCeiling, List<String> dietary) {
        String normalized = email.toLowerCase().trim();
        return users.findByEmail(normalized)
                .switchIfEmpty(hash(rawPassword).flatMap(h -> {
                    User u = new User();
                    u.setEmail(normalized);
                    u.setPasswordHash(h);
                    u.setDisplayName(displayName);
                    u.setRole(role);
                    u.setBudgetCeiling(budgetCeiling);
                    u.setDietaryTags(dietary == null ? new String[0] : dietary.toArray(new String[0]));
                    return users.save(u);
                }));
    }
}
