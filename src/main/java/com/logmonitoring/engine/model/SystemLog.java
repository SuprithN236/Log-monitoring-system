package com.logmonitoring.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_logs")
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, length = 64)
    private String serviceName;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "log_message", nullable = false, columnDefinition = "TEXT")
    private String logMessage;

    @Column(name = "log_timestamp", nullable = false)
    private LocalDateTime timestamp;

    protected SystemLog() {
    }

    public SystemLog(String serviceName, String severity, String logMessage, LocalDateTime timestamp) {
        this.serviceName = serviceName;
        this.severity = severity;
        this.logMessage = logMessage;
        this.timestamp = timestamp;
    }

    public Long getId() {
        return id;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getSeverity() {
        return severity;
    }

    public String getLogMessage() {
        return logMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}
