package com.logmonitoring.engine.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LogSubmissionResponse(
        UUID eventId,
        String routingKey,
        String status,
        LocalDateTime acceptedAt
) {
}
