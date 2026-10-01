package com.logmonitoring.engine.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int responseStatus = 200;

    @BeforeEach
    void startFakeResend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = (responseStatus == 200 ? "{\"id\":\"email_123\"}" : "{\"message\":\"Invalid API key\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void postsTheEmailWithBearerAuth() throws IOException {
        sender().send(new AlertEmail(List.of("oncall@example.com"), "[CRITICAL] svc: boom", "<p>boom</p>", "boom"));

        assertThat(authorization.get()).isEqualTo("Bearer re_test_key");
        JsonNode json = new ObjectMapper().readTree(body.get());
        assertThat(json.get("from").asText()).isEqualTo("Log Monitor <onboarding@resend.dev>");
        assertThat(json.get("to").get(0).asText()).isEqualTo("oncall@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("[CRITICAL] svc: boom");
        assertThat(json.get("html").asText()).isEqualTo("<p>boom</p>");
        assertThat(json.get("text").asText()).isEqualTo("boom");
    }

    @Test
    void throwsWhenTheProviderRejectsTheRequest() {
        responseStatus = 401;

        assertThatThrownBy(() -> sender().send(new AlertEmail(List.of("a@example.com"), "s", "h", "t")))
                .isInstanceOf(RestClientResponseException.class);
    }

    private ResendEmailSender sender() {
        AlertProperties properties = new AlertProperties("re_test_key", null, List.of("oncall@example.com"), null, 0,
                null, URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"), Duration.ofSeconds(5));
        return new ResendEmailSender(properties, RestClient.builder());
    }
}
