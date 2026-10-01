package com.logmonitoring.engine.controller;

import com.logmonitoring.engine.dto.AuthRequest;
import com.logmonitoring.engine.dto.AuthResponse;
import com.logmonitoring.engine.dto.UserResponse;
import com.logmonitoring.engine.security.JwtService.TokenUser;
import com.logmonitoring.engine.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody AuthRequest request) {
        return authService.signup(request.email(), request.password());
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** Lets the dashboard confirm a stored token is still valid when it loads. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal TokenUser user) {
        return new UserResponse(user.id(), user.email());
    }
}
