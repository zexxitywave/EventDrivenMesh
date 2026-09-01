package com.hacisimsek.common.logging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Map;

/**
 * Shared utility bean for publishing structured log events to the "service-logs" Kafka topic.
 *
 * Any microservice that imports common-library can inject this bean and call:
 *   logPublisher.info("order-service",  correlationId, "Order created: " + orderId);
 *   logPublisher.warn("payment-service", correlationId, "Retry attempt 2 for order: " + orderId);
 *   logPublisher.error("shipping-service", correlationId, "Shipment failed", exception, metadata);
 *
 * The logging-service consumes from "service-logs" and persists to MongoDB (logging_db).
 * All entries are indexed by serviceName, level, traceId, and timestamp.
 * Logs are auto-deleted after 30 days via MongoDB TTL index.
 *
 * This bean requires a KafkaTemplate<String, Object> in the application context.
 * Every service already has one configured in its KafkaConfig.
 */
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
@Slf4j
public class LogPublisher {

    private static final String TOPIC = "service-logs";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Public API ────────────────────────────────────────────────────────────

    public void info(String serviceName, String traceId, String message) {
        publish(serviceName, "INFO", traceId, message, null, null);
    }

    public void info(String serviceName, String traceId, String message, Map<String, Object> metadata) {
        publish(serviceName, "INFO", traceId, message, null, metadata);
    }

    public void warn(String serviceName, String traceId, String message) {
        publish(serviceName, "WARN", traceId, message, null, null);
    }

    public void warn(String serviceName, String traceId, String message, Map<String, Object> metadata) {
        publish(serviceName, "WARN", traceId, message, null, metadata);
    }

    public void error(String serviceName, String traceId, String message) {
        publish(serviceName, "ERROR", traceId, message, null, null);
    }

    public void error(String serviceName, String traceId, String message, Throwable ex) {
        publish(serviceName, "ERROR", traceId, message, ex, null);
    }

    public void error(String serviceName, String traceId, String message, Throwable ex,
                      Map<String, Object> metadata) {
        publish(serviceName, "ERROR", traceId, message, ex, metadata);
    }

    public void error(String serviceName, String traceId, String message,
                      Map<String, Object> metadata) {
        publish(serviceName, "ERROR", traceId, message, null, metadata);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void publish(String serviceName, String level, String traceId,
                         String message, Throwable ex, Map<String, Object> metadata) {
        try {
            ServiceLogEvent event = ServiceLogEvent.builder()
                    .serviceName(serviceName)
                    .level(level)
                    .message(message)
                    .traceId(traceId)
                    .exceptionClass(ex != null ? ex.getClass().getName() : null)
                    .stackTrace(ex != null ? stackTraceToString(ex) : null)
                    .metadata(metadata)
                    .build();

            kafkaTemplate.send(TOPIC, serviceName, event);

        } catch (Exception kafkaEx) {
            // Never let logging break the main flow — log locally and move on
            log.warn("[LogPublisher] Failed to publish to {}: {}", TOPIC, kafkaEx.getMessage());
        }
    }

    private String stackTraceToString(Throwable ex) {
        return ex.getClass().getName() + ": " + ex.getMessage() + "\n"
                + Arrays.stream(ex.getStackTrace())
                        .limit(10)
                        .map(StackTraceElement::toString)
                        .reduce("", (a, b) -> a + "\tat " + b + "\n");
    }
}
