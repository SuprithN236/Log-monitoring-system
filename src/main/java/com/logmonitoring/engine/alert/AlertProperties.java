package com.logmonitoring.engine.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * Email delivery for critical alerts via the Resend HTTP API. Alerts are sent only when an API key and
 * at least one recipient are configured; otherwise critical logs are still printed to the console.
 *
 * @param cooldown       minimum gap between emails for the same service; errors in between are counted
 * @param maxPerHour     cap across all services, protecting the provider's daily quota from error storms
 * @param dashboardUrl   linked from each email; on Render this defaults to the service's public URL
 */
@ConfigurationProperties(prefix = "log-engine.alerts.email")
public record AlertProperties(
        String resendApiKey,
        String from,
        List<String> to,
        Duration cooldown,
        int maxPerHour,
        String dashboardUrl,
        URI apiUrl,
        Duration timeout
) {

    public AlertProperties {
        resendApiKey = resendApiKey == null ? "" : resendApiKey.trim();
        from = StringUtils.hasText(from) ? from : "Log Monitor <onboarding@resend.dev>";
        to = to == null ? List.of() : to.stream().map(String::trim).filter(StringUtils::hasText).toList();
        cooldown = cooldown != null ? cooldown : Duration.ofMinutes(5);
        maxPerHour = maxPerHour > 0 ? maxPerHour : 20;
        dashboardUrl = dashboardUrl == null ? "" : dashboardUrl.trim();
        apiUrl = apiUrl != null ? apiUrl : URI.create("https://api.resend.com/emails");
        timeout = timeout != null ? timeout : Duration.ofSeconds(10);
    }

    public boolean enabled() {
        return StringUtils.hasText(resendApiKey) && !to.isEmpty();
    }
}
