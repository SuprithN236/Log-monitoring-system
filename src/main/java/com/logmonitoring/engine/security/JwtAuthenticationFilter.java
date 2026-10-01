package com.logmonitoring.engine.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates dashboard requests carrying {@code Authorization: Bearer <jwt>}. Requests without a
 * valid token continue unauthenticated, and the authorization rules decide whether that is allowed.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String ROLE = "USER";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtService.verify(header.substring(BEARER_PREFIX.length()).trim()).ifPresent(user ->
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            user, null, AuthorityUtils.createAuthorityList("ROLE_" + ROLE))));
        }
        chain.doFilter(request, response);
    }
}
