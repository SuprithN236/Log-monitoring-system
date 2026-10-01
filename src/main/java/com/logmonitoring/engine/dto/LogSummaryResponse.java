package com.logmonitoring.engine.dto;

/**
 * Database-wide counts, independent of how many rows the ledger endpoint returns.
 * Standard traces are everything routed to storage as {@code *.info} (INFO and WARNING).
 */
public record LogSummaryResponse(
        long total,
        long critical,
        long standard
) {
}
