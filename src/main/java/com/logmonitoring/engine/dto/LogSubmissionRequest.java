package com.logmonitoring.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code POST /api/v1/logs/submit}.
 * The service name becomes the first word of an AMQP topic routing key, so it is
 * restricted to a dot-free slug to keep wildcard bindings ({@code *.critical}) exact.
 */
public record LogSubmissionRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$",
                message = "must be 1-64 characters of letters, digits, '-' or '_' and start with a letter or digit")
        String serviceName,

        @NotBlank
        @Pattern(regexp = "(?i)^\\s*(CRITICAL|WARNING|INFO)\\s*$",
                message = "must be one of CRITICAL, WARNING, INFO")
        String severity,

        @NotBlank
        @Size(max = 65536)
        String logMessage
) {
}
