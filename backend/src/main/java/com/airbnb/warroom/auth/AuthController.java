package com.airbnb.warroom.auth;

import com.airbnb.warroom.auth.dto.AuthDtos.AuthResponse;
import com.airbnb.warroom.auth.dto.AuthDtos.LoginRequest;
import com.airbnb.warroom.auth.dto.AuthDtos.RefreshRequest;
import com.airbnb.warroom.auth.dto.AuthDtos.RegisterRequest;
import com.airbnb.warroom.user.UserDto;
import com.airbnb.warroom.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @PostMapping("/refresh")
    public Mono<AuthResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public Mono<Void> logout() {
        return SecurityUtil.currentUserId().flatMap(authService::logout);
    }

    @GetMapping("/me")
    public Mono<UserDto> me() {
        return SecurityUtil.currentUserId()
                .flatMap(users::findById)
                .map(UserDto::from);
    }
}
