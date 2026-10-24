package com.hacisimsek.logging.service;

import com.hacisimsek.logging.dto.LogSearchRequest;
import com.hacisimsek.logging.dto.LogStatsResponse;
import com.hacisimsek.logging.model.LogEntry;
import com.hacisimsek.logging.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogService {

    private final LogEntryRepository logEntryRepository;

    @Value("${app.logging.retention-days:30}")
    private int retentionDays;

    // ── Ingest ────────────────────────────────────────────────────────────────

    public LogEntry save(LogEntry entry) {
        if (entry.getId() == null)        entry.setId(UUID.randomUUID());
        if (entry.getTimestamp() == null)  entry.setTimestamp(Instant.now());
        if (entry.getExpiresAt() == null) {
            entry.setExpiresAt(entry.getTimestamp().plus(retentionDays, ChronoUnit.DAYS));
        }
        return logEntryRepository.save(entry);
    }

    // ── Search & Filter ───────────────────────────────────────────────────────

    public Page<LogEntry> search(LogSearchRequest req) {
        Pageable pageable = PageRequest.of(req.getPage(), req.getSize(),
                Sort.by(Sort.Direction.DESC, "timestamp"));

        String svc     = req.getServiceName();
        LogEntry.LogLevel lvl = req.getLevel();
        String kw      = req.getKeyword();
        Instant from   = req.getFrom();
        Instant to     = req.getTo();

        // Trace ID takes priority — returns all logs for that trace across services
        if (req.getTraceId() != null && !req.getTraceId().isBlank()) {
            return Page.empty(); // converted below via findByTraceId
        }

        // Keyword search
        if (kw != null && !kw.isBlank()) {
            return svc != null
                    ? logEntryRepository.searchByServiceAndMessage(svc, kw, pageable)
                    : logEntryRepository.searchByMessage(kw, pageable);
        }

        // Time range
        if (from != null && to != null) {
            return svc != null
                    ? logEntryRepository.findByServiceNameAndTimestampBetween(svc, from, to, pageable)
                    : logEntryRepository.findByTimestampBetween(from, to, pageable);
        }

        // Service + level
        if (svc != null && lvl != null) return logEntryRepository.findByServiceNameAndLevel(svc, lvl, pageable);
        if (svc != null)                return logEntryRepository.findByServiceName(svc, pageable);
        if (lvl != null)                return logEntryRepository.findByLevel(lvl, pageable);

        return logEntryRepository.findAll(pageable);
    }

    public List<LogEntry> getByTraceId(String traceId) {
        return logEntryRepository.findByTraceId(traceId);
    }

    public LogEntry getById(UUID id) {
        return logEntryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Log entry not found: " + id));
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    public LogStatsResponse getStats(int windowMinutes) {
        Instant since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);

        List<String> services = Arrays.asList(
                "order-service", "payment-service", "inventory-service",
                "shipping-service", "notification-service", "auth-service",
                "user-service", "product-service", "cart-service",
                "wishlist-service", "seller-service", "api-gateway"
        );

        Map<String, Long> errorsByService = services.stream()
                .collect(Collectors.toMap(
                        s -> s,
                        s -> logEntryRepository.countByServiceNameAndLevelAndTimestampAfter(
                                s, LogEntry.LogLevel.ERROR, since)));

        Map<String, Long> logsByService = services.stream()
                .collect(Collectors.toMap(
                        s -> s,
                        s -> logEntryRepository.countByServiceNameAndLevelAndTimestampAfter(
                                s, LogEntry.LogLevel.INFO, since)
                                + logEntryRepository.countByServiceNameAndLevelAndTimestampAfter(
                                        s, LogEntry.LogLevel.ERROR, since)
                                + logEntryRepository.countByServiceNameAndLevelAndTimestampAfter(
                                        s, LogEntry.LogLevel.WARN, since)));

        long errors = logEntryRepository.countByLevelAndTimestampAfter(LogEntry.LogLevel.ERROR, since);
        long warns  = logEntryRepository.countByLevelAndTimestampAfter(LogEntry.LogLevel.WARN, since);
        long infos  = logEntryRepository.countByLevelAndTimestampAfter(LogEntry.LogLevel.INFO, since);

        return LogStatsResponse.builder()
                .errorCount(errors)
                .warnCount(warns)
                .infoCount(infos)
                .totalLogs(errors + warns + infos)
                .errorsByService(errorsByService)
                .logsByService(logsByService)
                .windowMinutes(windowMinutes)
                .build();
    }

    // ── Recent errors — for alerting dashboard ────────────────────────────────

    public List<LogEntry> getRecentErrors(int minutes) {
        Instant since = Instant.now().minus(minutes, ChronoUnit.MINUTES);
        return logEntryRepository.findByLevelAndTimestampAfterOrderByTimestampDesc(
                LogEntry.LogLevel.ERROR, since);
    }
}
