package com.logmonitoring.engine.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Secrets for the two kinds of caller: producers submit logs with an API key, and people sign in to
 * the dashboard and receive a JWT signed with {@code jwtSecret}.
 * <p>
 * Spring leaves an unset {@code ${VAR}} placeholder as literal text, which would otherwise become a
 * guessable secret, so startup is refused when a value still looks like a placeholder.
 */
@Validated
@ConfigurationProperties(prefix = "log-engine.security")
public record SecurityProperties(

        @NotBlank
        @Size(min = 12, message = "must be at least 12 characters")
        @Pattern(regexp = "^(?!\\$\\{).*", message = "is not set; provide INGEST_API_KEY")
        String ingestApiKey,

        @NotBlank
        @Size(min = 32, message = "must be at least 32 characters")
        @Pattern(regexp = "^(?!\\$\\{).*", message = "is not set; provide JWT_SECRET")
        String jwtSecret,

        @NotNull
        Duration jwtExpiration,

        int authAttemptsPerMinute
) {

    public SecurityProperties {
        authAttemptsPerMinute = authAttemptsPerMinute > 0 ? authAttemptsPerMinute : 10;
    }
}
