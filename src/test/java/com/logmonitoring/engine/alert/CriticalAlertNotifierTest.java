package com.logmonitoring.engine.alert;

import com.logmonitoring.engine.dto.LogEvent;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.logmonitoring.engine.alert.CriticalAlertNotifier.Outcome.DISABLED;
import static com.logmonitoring.engine.alert.CriticalAlertNotifier.Outcome.FAILED;
import static com.logmonitoring.engine.alert.CriticalAlertNotifier.Outcome.SENT;
import static com.logmonitoring.engine.alert.CriticalAlertNotifier.Outcome.THROTTLED;
import static org.assertj.core.api.Assertions.assertThat;

class CriticalAlertNotifierTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T12:00:00Z"));
    private final List<AlertEmail> sent = new ArrayList<>();
    private final EmailSender recordingSender = sent::add;

    @Test
    void sendsNothingWhenNotConfigured() {
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("", 20), recordingSender, clock);

        assertThat(notifier.notify(event("payment-service", "boom"))).isEqualTo(DISABLED);
        assertThat(sent).isEmpty();
    }

    @Test
    void throttlesRepeatsPerServiceAndReportsTheSuppressedCount() {
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("re_key", 20), recordingSender, clock);

        assertThat(notifier.notify(event("payment-service", "first"))).isEqualTo(SENT);
        assertThat(notifier.notify(event("payment-service", "second"))).isEqualTo(THROTTLED);
        assertThat(notifier.notify(event("payment-service", "third"))).isEqualTo(THROTTLED);
        assertThat(notifier.notify(event("auth-service", "other service is independent"))).isEqualTo(SENT);

        clock.advance(Duration.ofMinutes(5));
        assertThat(notifier.notify(event("payment-service", "after cooldown"))).isEqualTo(SENT);

        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).text()).doesNotContain("more critical");
        assertThat(sent.get(2).text()).contains("+2 more critical errors from this service since the last alert.");
    }

    @Test
    void capsEmailsPerHourAcrossServices() {
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("re_key", 2), recordingSender, clock);

        assertThat(notifier.notify(event("a", "x"))).isEqualTo(SENT);
        assertThat(notifier.notify(event("b", "x"))).isEqualTo(SENT);
        assertThat(notifier.notify(event("c", "x"))).isEqualTo(THROTTLED);

        clock.advance(Duration.ofHours(1));
        assertThat(notifier.notify(event("c", "x"))).isEqualTo(SENT);
        assertThat(sent.get(2).text()).contains("+1 more critical error from this service");
    }

    @Test
    void reportsFailureWithoutThrowing() {
        EmailSender failing = email -> {
            throw new IllegalStateException("provider down");
        };
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("re_key", 20), failing, clock);

        assertThat(notifier.notify(event("payment-service", "boom"))).isEqualTo(FAILED);
    }

    @Test
    void failedSendDoesNotStartTheCooldownAndIsReportedNextTime() {
        boolean[] providerDown = {true};
        EmailSender flaky = email -> {
            if (providerDown[0]) throw new IllegalStateException("provider down");
            sent.add(email);
        };
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("re_key", 20), flaky, clock);

        assertThat(notifier.notify(event("payment-service", "lost"))).isEqualTo(FAILED);
        providerDown[0] = false;
        assertThat(notifier.notify(event("payment-service", "retry right away"))).isEqualTo(SENT);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).text()).contains("+1 more critical error from this service");
    }

    @Test
    void escapesLogContentInHtmlAndKeepsSubjectToOneLine() {
        CriticalAlertNotifier notifier = new CriticalAlertNotifier(properties("re_key", 20), recordingSender, clock);

        notifier.notify(event("payment-service", "<script>alert(1)</script> failed\n\tat com.pay.Ledger(Ledger.java:88)"));

        AlertEmail email = sent.get(0);
        assertThat(email.to()).containsExactly("oncall@example.com");
        assertThat(email.subject()).isEqualTo("[CRITICAL] payment-service: <script>alert(1)</script> failed");
        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
        assertThat(email.html()).contains("href=\"https://logs.example.com\"");
        assertThat(email.text()).contains("at com.pay.Ledger(Ledger.java:88)");
    }

    private static AlertProperties properties(String apiKey, int maxPerHour) {
        return new AlertProperties(apiKey, null, List.of("oncall@example.com"), Duration.ofMinutes(5), maxPerHour,
                "https://logs.example.com", URI.create("https://api.resend.com/emails"), Duration.ofSeconds(5));
    }

    private static LogEvent event(String service, String message) {
        return new LogEvent(UUID.randomUUID(), service, "CRITICAL", message, LocalDateTime.now(ZoneOffset.UTC));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
