package com.hacisimsek.logging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Central log entry stored in MongoDB.
 *
 * Two sources of logs:
 * 1. Direct: services publish LogEntry events to the "service-logs" Kafka topic
 *    using the shared LogPublisher utility.
 * 2. Derived: logging-service intercepts business events (order-events, payment-events, etc.)
 *    and creates log entries automatically.
 *
 * MongoDB TTL index on 'timestamp' auto-deletes logs older than retention-days.
 */
@Document(collection = "log_entries")
@CompoundIndexes({
    @CompoundIndex(name = "service_level_time", def = "{'serviceName': 1, 'level': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "trace_id_idx",        def = "{'traceId': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {

    @Id
    private UUID id;

    /** Name of the originating service: order-service, payment-service, etc. */
    @Indexed
    private String serviceName;

    /** Log level: INFO, WARN, ERROR, DEBUG */
    @Indexed
    private LogLevel level;

    /** The actual log message. */
    private String message;

    /**
     * Optional trace/correlation ID — links related log entries across services
     * for a single user request or saga transaction.
     */
    private String traceId;

    /** Optional exception class name for ERROR entries. */
    private String exceptionClass;

    /** Full stack trace for ERROR entries. */
    private String stackTrace;

    /** HTTP method + path for API access logs: "GET /api/orders/123" */
    private String endpoint;

    /** HTTP status code for API access logs. */
    private Integer httpStatus;

    /** Request duration in milliseconds. */
    private Long durationMs;

    /** Source: KAFKA_EVENT (derived from business event) or SERVICE_LOG (direct publish). */
    private LogSource source;

    /** Extra context fields — flexible key-value pairs. */
    private Map<String, Object> metadata;

    /** TTL-indexed — MongoDB will delete this document after retention period. */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    @Indexed
    private Instant timestamp;

    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR
    }

    public enum LogSource {
        SERVICE_LOG,   // explicitly published by service
        KAFKA_EVENT,   // derived from a business Kafka event
        API_ACCESS     // HTTP request/response log
    }
}
