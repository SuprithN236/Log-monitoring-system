package com.logmonitoring.engine.dto;

import com.logmonitoring.engine.model.SystemLog;

import java.time.LocalDateTime;

public record LogEntryResponse(
        Long id,
        String serviceName,
        String severity,
        String logMessage,
        LocalDateTime timestamp
) {

    public static LogEntryResponse from(SystemLog log) {
        return new LogEntryResponse(log.getId(), log.getServiceName(), log.getSeverity(),
                log.getLogMessage(), log.getTimestamp());
    }
}
