package com.logmonitoring.engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Producers authenticate with an API key on the ingest endpoint only. People sign up or sign in at
 * {@code /api/v1/auth/*} and send the returned JWT as a bearer token to read logs. The dashboard's static
 * files are public so the sign-in page can load; every log endpoint requires a token.
 * CSRF protection is off because no credential is ever attached by the browser automatically.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityProperties properties,
                                                   JwtService jwtService,
                                                   ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new AuthRateLimitFilter(properties.authAttemptsPerMinute(), objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new IngestApiKeyFilter(properties.ingestApiKey(), objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, IngestApiKeyFilter.INGEST_PATH).hasRole(IngestApiKeyFilter.ROLE)
                        .requestMatchers("/api/**", "/actuator/**").hasRole(JwtAuthenticationFilter.ROLE)
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(jsonUnauthorized(objectMapper)))
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** A plain 401 body; no WWW-Authenticate challenge, so browsers never show a native sign-in popup. */
    private static AuthenticationEntryPoint jsonUnauthorized(ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign in to continue"));
        };
    }
}
