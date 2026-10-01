package com.logmonitoring.engine.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message published to the topic exchange. The timestamp is captured at ingestion so
 * persisted records reflect when the log arrived, not when a consumer drained it.
 */
public record LogEvent(
        UUID eventId,
        String serviceName,
        String severity,
        String logMessage,
        LocalDateTime timestamp
) {
}
