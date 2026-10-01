package com.logmonitoring.engine.controller;

import com.logmonitoring.engine.dto.LogEntryResponse;
import com.logmonitoring.engine.dto.LogSummaryResponse;
import com.logmonitoring.engine.model.Severity;
import com.logmonitoring.engine.repository.LogRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/logs", produces = MediaType.APPLICATION_JSON_VALUE)
public class LogQueryController {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("timestamp"), Sort.Order.desc("id"));

    private final LogRepository logRepository;

    public LogQueryController(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<LogEntryResponse> recentLogs(
            @RequestParam(defaultValue = "500") @Min(1) @Max(2000) int limit) {
        return logRepository.findAll(PageRequest.of(0, limit, NEWEST_FIRST))
                .map(LogEntryResponse::from)
                .getContent();
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public LogSummaryResponse summary() {
        long total = 0;
        long critical = 0;
        for (LogRepository.SeverityCount row : logRepository.countBySeverity()) {
            total += row.getTotal();
            if (Severity.CRITICAL.name().equals(row.getSeverity())) {
                critical += row.getTotal();
            }
        }
        return new LogSummaryResponse(total, critical, total - critical);
    }
}
