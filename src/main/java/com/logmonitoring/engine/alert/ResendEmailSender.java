package com.logmonitoring.engine.alert;

import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends email through Resend's HTTPS API (https://resend.com/docs/api-reference/emails/send-email).
 * HTTPS is used rather than SMTP because free hosting tiers commonly block outbound SMTP ports.
 */
@Component
public class ResendEmailSender implements EmailSender {

    private final AlertProperties properties;
    private final RestClient restClient;

    public ResendEmailSender(AlertProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
        requestFactory.setReadTimeout(properties.timeout());
        // Buffering sends a Content-Length instead of a chunked stream; alert bodies are small.
        this.restClient = restClientBuilder.requestFactory(new BufferingClientHttpRequestFactory(requestFactory)).build();
    }

    @Override
    public void send(AlertEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", properties.from());
        body.put("to", email.to());
        body.put("subject", email.subject());
        body.put("html", email.html());
        body.put("text", email.text());

        restClient.post()
                .uri(properties.apiUrl())
                .header("Authorization", "Bearer " + properties.resendApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
