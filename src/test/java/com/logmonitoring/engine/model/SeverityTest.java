package com.logmonitoring.engine.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityTest {

    @Test
    void criticalRoutesToCriticalBinding() {
        assertThat(Severity.CRITICAL.routingKeyFor("payment-service")).isEqualTo("payment-service.critical");
    }

    @Test
    void infoAndWarningRouteToStorageBinding() {
        assertThat(Severity.INFO.routingKeyFor("auth-service")).isEqualTo("auth-service.info");
        assertThat(Severity.WARNING.routingKeyFor("auth-service")).isEqualTo("auth-service.info");
    }

    @Test
    void parseIsCaseInsensitiveAndRejectsUnknownValues() {
        assertThat(Severity.parse(" critical ")).contains(Severity.CRITICAL);
        assertThat(Severity.parse("DEBUG")).isEmpty();
        assertThat(Severity.parse(null)).isEmpty();
    }
}
