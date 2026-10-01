package com.logmonitoring.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caps sign-in and sign-up attempts per client IP in one-minute windows to slow password guessing and
 * bulk account creation. State is per instance and in memory, which suits a single free-tier service.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final long WINDOW_MILLIS = 60_000;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxAttempts;
    private final ObjectMapper objectMapper;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(int maxAttemptsPerMinute, ObjectMapper objectMapper) {
        this.maxAttempts = maxAttemptsPerMinute;
        this.objectMapper = objectMapper;
    }

    private record Window(long startedAt, AtomicInteger count) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && request.getRequestURI().startsWith(AUTH_PATH_PREFIX));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(window -> now - window.startedAt() >= WINDOW_MILLIS);
        }
        // forward-headers-strategy resolves the real client address behind the hosting proxy.
        Window window = windows.compute(request.getRemoteAddr(), (ip, current) ->
                current == null || now - current.startedAt() >= WINDOW_MILLIS
                        ? new Window(now, new AtomicInteger())
                        : current);

        if (window.count().incrementAndGet() > maxAttempts) {
            long retryAfterSeconds = Math.max(1, (window.startedAt() + WINDOW_MILLIS - now + 999) / 1000);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts. Try again in " + retryAfterSeconds + " seconds."));
            return;
        }
        chain.doFilter(request, response);
    }
}
