package com.logmonitoring.engine.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies the HS256 session tokens the dashboard sends as {@code Authorization: Bearer}. */
@Service
public class JwtService {

    private static final String ISSUER = "log-monitoring-engine";
    private static final String EMAIL_CLAIM = "email";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(SecurityProperties properties) {
        this.key = Keys.hmacShaKeyFor(sha256(properties.jwtSecret()));
        this.expiration = properties.jwtExpiration();
    }

    public record TokenUser(Long id, String email) {
    }

    public String issue(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim(EMAIL_CLAIM, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<TokenUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new TokenUser(Long.valueOf(claims.getSubject()), claims.get(EMAIL_CLAIM, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }

    // Hashing gives a full-strength 256-bit HMAC key from any sufficiently long secret string.
    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
