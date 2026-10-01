package com.logmonitoring.engine.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Credentials for the two kinds of caller: producers submit logs with an API key, and people use the
 * dashboard and read API with a username and password.
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
        @Pattern(regexp = "^(?!\\$\\{).*", message = "is not set; provide DASHBOARD_USERNAME")
        String dashboardUsername,

        @NotBlank
        @Size(min = 12, message = "must be at least 12 characters")
        @Pattern(regexp = "^(?!\\$\\{).*", message = "is not set; provide DASHBOARD_PASSWORD")
        String dashboardPassword
) {
}
