package com.logmonitoring.engine.alert;

import com.logmonitoring.engine.dto.LogEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Emails critical logs to the configured recipients, at most once per service per cooldown. Errors that
 * arrive during a cooldown are counted and reported in the next email, so a failure loop produces one
 * email with a tally instead of hundreds. Never throws: a failed email must not fail or retry the log.
 */
@Service
public class CriticalAlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(CriticalAlertNotifier.class);
    private static final int MAX_EMAIL_MESSAGE_CHARS = 4_000;
    private static final int MAX_SUBJECT_SUMMARY_CHARS = 80;
    private static final Duration HOUR = Duration.ofHours(1);

    private final AlertProperties properties;
    private final EmailSender emailSender;
    private final Clock clock;

    /** Per-service throttle state, guarded by {@code this}. */
    private final Map<String, ServiceState> services = new HashMap<>();
    private final Deque<Instant> sentInLastHour = new ArrayDeque<>();

    private static final class ServiceState {
        private Instant lastSentAt;
        private int suppressedSinceLastEmail;
    }

    public enum Outcome { SENT, THROTTLED, DISABLED, FAILED }

    @Autowired
    public CriticalAlertNotifier(AlertProperties properties, EmailSender emailSender) {
        this(properties, emailSender, Clock.systemUTC());
    }

    CriticalAlertNotifier(AlertProperties properties, EmailSender emailSender, Clock clock) {
        this.properties = properties;
        this.emailSender = emailSender;
        this.clock = clock;
        if (properties.enabled()) {
            log.info("Critical alerts will be emailed to {} recipient(s), at most once per service every {}",
                    properties.to().size(), properties.cooldown());
        } else {
            log.info("Email alerts are off; set RESEND_API_KEY and ALERT_EMAIL_TO to enable them");
        }
    }

    public Outcome notify(LogEvent event) {
        if (!properties.enabled()) {
            return Outcome.DISABLED;
        }
        Reservation reservation = reserveSlot(event.serviceName());
        if (reservation == null) {
            return Outcome.THROTTLED;
        }
        try {
            emailSender.send(compose(event, reservation.suppressed()));
            return Outcome.SENT;
        } catch (RuntimeException e) {
            release(event.serviceName(), reservation);
            log.error("Failed to email critical alert for event {} ({}): {}",
                    event.eventId(), event.serviceName(), e.getMessage());
            return Outcome.FAILED;
        }
    }

    /** What a send claimed, so a failed send can hand its slot back. */
    private record Reservation(Instant at, Instant previousSentAt, int suppressed) {
    }

    /**
     * Claims the right to send for this service now, or returns null when the service is cooling down or
     * the hourly cap is reached (the alert is then counted toward the next email's tally).
     */
    private synchronized Reservation reserveSlot(String serviceName) {
        Instant now = clock.instant();
        ServiceState state = services.computeIfAbsent(serviceName, name -> new ServiceState());

        while (!sentInLastHour.isEmpty() && !sentInLastHour.peekFirst().isAfter(now.minus(HOUR))) {
            sentInLastHour.pollFirst();
        }
        boolean coolingDown = state.lastSentAt != null && now.isBefore(state.lastSentAt.plus(properties.cooldown()));
        if (coolingDown || sentInLastHour.size() >= properties.maxPerHour()) {
            state.suppressedSinceLastEmail++;
            return null;
        }

        Reservation reservation = new Reservation(now, state.lastSentAt, state.suppressedSinceLastEmail);
        state.lastSentAt = now;
        state.suppressedSinceLastEmail = 0;
        sentInLastHour.addLast(now);
        return reservation;
    }

    /** Undoes a reservation after a failed send, so the next critical error tries again immediately. */
    private synchronized void release(String serviceName, Reservation reservation) {
        ServiceState state = services.get(serviceName);
        if (state.lastSentAt == reservation.at()) {
            state.lastSentAt = reservation.previousSentAt();
        }
        // The undelivered alert, and any it would have reported, carry over to the next email.
        state.suppressedSinceLastEmail += reservation.suppressed() + 1;
        sentInLastHour.removeLastOccurrence(reservation.at());
    }

    AlertEmail compose(LogEvent event, int suppressed) {
        String message = event.logMessage().length() <= MAX_EMAIL_MESSAGE_CHARS
                ? event.logMessage()
                : event.logMessage().substring(0, MAX_EMAIL_MESSAGE_CHARS) + "\n…[truncated]";
        String firstLine = event.logMessage().strip().lines().findFirst().orElse("").strip();
        String summary = firstLine.length() <= MAX_SUBJECT_SUMMARY_CHARS
                ? firstLine
                : firstLine.substring(0, MAX_SUBJECT_SUMMARY_CHARS) + "…";
        String subject = "[CRITICAL] " + event.serviceName() + ": " + summary;
        String repeats = suppressed > 0
                ? "+" + suppressed + " more critical " + (suppressed == 1 ? "error" : "errors")
                  + " from this service since the last alert."
                : null;
        String dashboardUrl = properties.dashboardUrl();

        StringBuilder text = new StringBuilder()
                .append("Critical error from ").append(event.serviceName()).append("\n\n")
                .append("Time (UTC): ").append(event.timestamp()).append('\n')
                .append("Event ID:   ").append(event.eventId()).append("\n\n")
                .append(message).append('\n');
        if (repeats != null) text.append('\n').append(repeats).append('\n');
        if (!dashboardUrl.isEmpty()) text.append("\nOpen the dashboard: ").append(dashboardUrl).append('\n');

        String html = """
                <div style="font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:640px;margin:0 auto;color:#0f172a">
                  <div style="background:#b91c1c;color:#fff;padding:14px 18px;border-radius:8px 8px 0 0;font-weight:600">
                    Critical error from %s
                  </div>
                  <div style="border:1px solid #e2e8f0;border-top:0;border-radius:0 0 8px 8px;padding:18px">
                    <table style="font-size:14px;margin-bottom:14px">
                      <tr><td style="color:#64748b;padding-right:12px">Service</td><td><b>%s</b></td></tr>
                      <tr><td style="color:#64748b;padding-right:12px">Time (UTC)</td><td>%s</td></tr>
                      <tr><td style="color:#64748b;padding-right:12px">Event ID</td><td style="font-family:monospace">%s</td></tr>
                    </table>
                    <pre style="background:#0f172a;color:#e2e8f0;padding:14px;border-radius:6px;font-size:12px;white-space:pre-wrap;word-break:break-word;margin:0">%s</pre>
                    %s
                    %s
                  </div>
                </div>
                """.formatted(
                escape(event.serviceName()),
                escape(event.serviceName()),
                escape(String.valueOf(event.timestamp())),
                escape(String.valueOf(event.eventId())),
                escape(message),
                repeats == null ? "" : "<p style=\"color:#b91c1c;font-weight:600;margin:14px 0 0\">" + escape(repeats) + "</p>",
                dashboardUrl.isEmpty() ? "" : "<p style=\"margin:16px 0 0\"><a href=\"" + escape(dashboardUrl)
                        + "\" style=\"background:#2563eb;color:#fff;padding:8px 14px;border-radius:6px;text-decoration:none\">Open the dashboard</a></p>");

        return new AlertEmail(properties.to(), subject, html, text.toString());
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
