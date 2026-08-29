package com.hacisimsek.logging.controller;

import com.hacisimsek.logging.dto.LogSearchRequest;
import com.hacisimsek.logging.dto.LogStatsResponse;
import com.hacisimsek.logging.model.LogEntry;
import com.hacisimsek.logging.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    /**
     * Search logs with optional filters.
     * GET /api/logs?serviceName=order-service&level=ERROR&page=0&size=20
     * GET /api/logs?keyword=payment+failed&from=2024-01-01T00:00:00Z
     */
    @GetMapping
    public ResponseEntity<Page<LogEntry>> search(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) LogEntry.LogLevel level,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {

        LogSearchRequest req = new LogSearchRequest();
        req.setServiceName(serviceName);
        req.setLevel(level);
        req.setKeyword(keyword);
        req.setTraceId(traceId);
        req.setFrom(from);
        req.setTo(to);
        req.setPage(page);
        req.setSize(size);

        // traceId search returns a list — wrap in simple response
        if (traceId != null && !traceId.isBlank()) {
            List<LogEntry> entries = logService.getByTraceId(traceId);
            return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(entries));
        }

        return ResponseEntity.ok(logService.search(req));
    }

    /** Get a single log entry by ID. */
    @GetMapping("/{id}")
    public ResponseEntity<LogEntry> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(logService.getById(id));
    }

    /**
     * Get all logs tied to a specific trace/correlation ID.
     * Useful for tracing a request across multiple services in a saga.
     * GET /api/logs/trace/550e8400-e29b-41d4-a716-446655440000
     */
    @GetMapping("/trace/{traceId}")
    public ResponseEntity<List<LogEntry>> getByTrace(@PathVariable String traceId) {
        return ResponseEntity.ok(logService.getByTraceId(traceId));
    }

    /**
     * Aggregate stats over a rolling time window.
     * GET /api/logs/stats?windowMinutes=60
     */
    @GetMapping("/stats")
    public ResponseEntity<LogStatsResponse> getStats(
            @RequestParam(defaultValue = "60") int windowMinutes) {
        return ResponseEntity.ok(logService.getStats(windowMinutes));
    }

    /**
     * Get recent ERROR logs — useful for a monitoring dashboard.
     * GET /api/logs/errors/recent?minutes=10
     */
    @GetMapping("/errors/recent")
    public ResponseEntity<List<LogEntry>> getRecentErrors(
            @RequestParam(defaultValue = "10") int minutes) {
        return ResponseEntity.ok(logService.getRecentErrors(minutes));
    }
}
