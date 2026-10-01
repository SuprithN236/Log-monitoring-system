package com.logmonitoring.engine.controller;

import com.logmonitoring.engine.config.RabbitMQConfig;
import com.logmonitoring.engine.dto.LogEvent;
import com.logmonitoring.engine.dto.LogSubmissionRequest;
import com.logmonitoring.engine.dto.LogSubmissionResponse;
import com.logmonitoring.engine.model.Severity;
import jakarta.validation.Valid;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs")
public class LogIngressController {

    private final RabbitTemplate rabbitTemplate;

    public LogIngressController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping(value = "/submit",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LogSubmissionResponse submit(@Valid @RequestBody LogSubmissionRequest request) {
        Severity severity = Severity.parse(request.severity())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported severity: " + request.severity()));
        String serviceName = request.serviceName().toLowerCase(Locale.ROOT);
        String routingKey = severity.routingKeyFor(serviceName);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        LogEvent event = new LogEvent(UUID.randomUUID(), serviceName, severity.name(), request.logMessage(), now);
        rabbitTemplate.convertAndSend(RabbitMQConfig.LOG_TOPIC_EXCHANGE, routingKey, event,
                new CorrelationData(event.eventId().toString()));

        return new LogSubmissionResponse(event.eventId(), routingKey, "QUEUED", now);
    }
}
