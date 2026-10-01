package com.logmonitoring.engine.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported log severities and the routing-key suffix each one is published under.
 * WARNING is a standard trace: it is stored alongside INFO rather than paging anyone.
 */
public enum Severity {

    CRITICAL("critical"),
    WARNING("info"),
    INFO("info");

    private final String routingSuffix;

    Severity(String routingSuffix) {
        this.routingSuffix = routingSuffix;
    }

    public String routingKeyFor(String serviceName) {
        return serviceName + "." + routingSuffix;
    }

    public static Optional<Severity> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Severity.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
