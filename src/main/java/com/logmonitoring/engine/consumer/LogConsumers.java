package com.logmonitoring.engine.consumer;

import com.logmonitoring.engine.alert.CriticalAlertNotifier;
import com.logmonitoring.engine.config.RabbitMQConfig;
import com.logmonitoring.engine.dto.LogEvent;
import com.logmonitoring.engine.model.SystemLog;
import com.logmonitoring.engine.repository.LogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LogConsumers {

    private static final Logger log = LoggerFactory.getLogger(LogConsumers.class);
    private static final Logger alertTelemetry = LoggerFactory.getLogger("TELEMETRY.ALERT");

    private static final String ALERT_BORDER = "!".repeat(80);

    private final LogRepository logRepository;
    private final CriticalAlertNotifier alertNotifier;

    public LogConsumers(LogRepository logRepository, CriticalAlertNotifier alertNotifier) {
        this.logRepository = logRepository;
        this.alertNotifier = alertNotifier;
    }

    @RabbitListener(queues = RabbitMQConfig.CRITICAL_ALERTS_QUEUE,
            containerFactory = "criticalListenerContainerFactory")
    public void onCriticalAlert(LogEvent event) {
        alertTelemetry.error("""

                {}
                !!  CRITICAL PRODUCTION ALERT
                {}
                !!  Event ID   : {}
                !!  Service    : {}
                !!  Severity   : {}
                !!  Occurred   : {} UTC
                !!  Message    :
                {}
                {}""",
                ALERT_BORDER, ALERT_BORDER,
                event.eventId(), event.serviceName(), event.severity(), event.timestamp(),
                event.logMessage(),
                ALERT_BORDER);
        // Persist before emailing: if the save fails, the message is retried and only then emailed,
        // so a retry never produces a second email for the same event.
        persist(event);
        CriticalAlertNotifier.Outcome outcome = alertNotifier.notify(event);
        alertTelemetry.info("Alert email for event {}: {}", event.eventId(), outcome);
    }

    @RabbitListener(queues = RabbitMQConfig.INFO_STORAGE_QUEUE,
            containerFactory = "storageListenerContainerFactory")
    public void onStorageLog(LogEvent event) {
        persist(event);
    }

    private void persist(LogEvent event) {
        SystemLog entity = new SystemLog(
                event.serviceName(),
                event.severity(),
                event.logMessage(),
                event.timestamp());
        SystemLog saved = logRepository.save(entity);
        log.debug("Persisted log event {} as system_logs.id={}", event.eventId(), saved.getId());
    }
}
