package com.hacisimsek.common.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Shared DTO published to the "service-logs" Kafka topic by any microservice.
 * The logging-service consumes this and persists it to MongoDB as a LogEntry.
 *
 * Payload keys match what LogEventConsumer.parseServiceLog() expects:
 *   serviceName, level, message, traceId, endpoint, exceptionClass, stackTrace,
 *   httpStatus, durationMs, metadata, timestamp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceLogEvent {

    /** Name of the originating service: order-service, payment-service, etc. */
    private String serviceName;

    /** Log level: INFO, WARN, ERROR, DEBUG */
    private String level;

    /** The actual log message. */
    private String message;

    /**
     * Correlation / trace ID — links all log entries across services
     * for a single saga transaction.
     */
    private String traceId;

    /** Optional HTTP endpoint for API access logs: "POST /api/orders" */
    private String endpoint;

    /** Exception class name for ERROR entries. */
    private String exceptionClass;

    /** Full stack trace for ERROR entries. */
    private String stackTrace;

    /** HTTP status code for API access logs. */
    private Integer httpStatus;

    /** Request duration in milliseconds. */
    private Long durationMs;

    /** Extra context: orderId, paymentId, reason, etc. */
    private Map<String, Object> metadata;

    /** Event creation timestamp. */
    @Builder.Default
    private Instant timestamp = Instant.now();
}
