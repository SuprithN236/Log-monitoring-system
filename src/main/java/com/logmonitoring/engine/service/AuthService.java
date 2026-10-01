package com.logmonitoring.engine.service;

import com.logmonitoring.engine.dto.AuthResponse;
import com.logmonitoring.engine.dto.UserResponse;
import com.logmonitoring.engine.model.AppUser;
import com.logmonitoring.engine.repository.AppUserRepository;
import com.logmonitoring.engine.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    /** Compared against when the email is unknown, so sign-in timing does not reveal which emails exist. */
    private final String timingEqualizerHash;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.timingEqualizerHash = passwordEncoder.encode("timing-equalizer");
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
        public EmailAlreadyRegisteredException() {
            super("An account with this email already exists");
        }
    }

    @Transactional
    public AuthResponse signup(String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }
        try {
            AppUser user = userRepository.saveAndFlush(new AppUser(
                    normalizedEmail, passwordEncoder.encode(rawPassword), LocalDateTime.now(ZoneOffset.UTC)));
            return issueToken(user);
        } catch (DataIntegrityViolationException e) {
            // Two concurrent sign-ups with the same email: the unique constraint decides the winner.
            throw new EmailAlreadyRegisteredException();
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String rawPassword) {
        AppUser user = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        String hash = user != null ? user.getPasswordHash() : timingEqualizerHash;
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);
        if (user == null || !passwordMatches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return issueToken(user);
    }

    private AuthResponse issueToken(AppUser user) {
        return new AuthResponse(jwtService.issue(user.getId(), user.getEmail()), "Bearer",
                jwtService.expirationSeconds(), new UserResponse(user.getId(), user.getEmail()));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
