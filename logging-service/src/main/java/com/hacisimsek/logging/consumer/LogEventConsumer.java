package com.hacisimsek.logging.consumer;

import com.hacisimsek.logging.model.LogEntry;
import com.hacisimsek.logging.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Consumes from:
 * - service-logs    : explicit log events published by any microservice
 * - order-events    : business events auto-converted to log entries
 * - payment-events  : business events auto-converted to log entries
 * - inventory-events: business events auto-converted to log entries
 * - shipping-events : business events auto-converted to log entries
 *
 * All records are deserialized as Map<String,Object> — no shared DTOs needed.
 * This keeps the logging service fully decoupled from other services' class hierarchies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogEventConsumer {

    private final LogService logService;

    // ── Explicit service log events ───────────────────────────────────────────

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeServiceLogs(ConsumerRecord<String, Object> record) {
        try {
            LogEntry entry = parseServiceLog(record);
            logService.save(entry);
        } catch (Exception e) {
            log.warn("Failed to process service-logs record: {}", e.getMessage());
        }
    }

    // ── Business event topics — derived log entries ───────────────────────────

    @KafkaListener(topics = "order-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderEvents(ConsumerRecord<String, Object> record) {
        saveEventLog("order-service", "order-events", record);
    }

    @KafkaListener(topics = "payment-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumePaymentEvents(ConsumerRecord<String, Object> record) {
        saveEventLog("payment-service", "payment-events", record);
    }

    @KafkaListener(topics = "inventory-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeInventoryEvents(ConsumerRecord<String, Object> record) {
        saveEventLog("inventory-service", "inventory-events", record);
    }

    @KafkaListener(topics = "shipping-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeShippingEvents(ConsumerRecord<String, Object> record) {
        saveEventLog("shipping-service", "shipping-events", record);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses an explicit service-log event.
     * Expected payload keys: serviceName, level, message, traceId, metadata, timestamp
     */
    @SuppressWarnings("unchecked")
    private LogEntry parseServiceLog(ConsumerRecord<String, Object> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value();

        String serviceName = getString(payload, "serviceName", "unknown");
        String levelStr    = getString(payload, "level", "INFO");
        String message     = getString(payload, "message", "(no message)");
        String traceId     = getString(payload, "traceId", null);
        String endpoint    = getString(payload, "endpoint", null);
        String exClass     = getString(payload, "exceptionClass", null);
        String stackTrace  = getString(payload, "stackTrace", null);

        Integer httpStatus = payload.get("httpStatus") instanceof Number n ? n.intValue() : null;
        Long durationMs    = payload.get("durationMs")   instanceof Number n ? n.longValue() : null;

        LogEntry.LogLevel level;
        try {
            level = LogEntry.LogLevel.valueOf(levelStr.toUpperCase());
        } catch (IllegalArgumentException ex) {
            level = LogEntry.LogLevel.INFO;
        }

        return LogEntry.builder()
                .serviceName(serviceName)
                .level(level)
                .message(message)
                .traceId(traceId)
                .endpoint(endpoint)
                .exceptionClass(exClass)
                .stackTrace(stackTrace)
                .httpStatus(httpStatus)
                .durationMs(durationMs)
                .source(LogEntry.LogSource.SERVICE_LOG)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Converts a business event from any topic into an INFO log entry.
     * Errors/failures are detected by class name containing "Failed".
     */
    @SuppressWarnings("unchecked")
    private void saveEventLog(String defaultService, String topic, ConsumerRecord<String, Object> record) {
        try {
            Object rawValue = record.value();

            // Guard: value must be a Map (deserialized from JSON)
            if (!(rawValue instanceof Map)) {
                log.warn("[LogConsumer] Skipping non-Map record on topic={} type={}",
                        topic, rawValue != null ? rawValue.getClass().getSimpleName() : "null");
                return;
            }

            Map<String, Object> payload = (Map<String, Object>) rawValue;

            // Derive type from __TypeId__ header or payload fields
            String eventType = extractEventType(record, payload);
            boolean isFailure = eventType.toLowerCase().contains("failed")
                    || eventType.toLowerCase().contains("fail");

            String orderId       = extractStringValue(payload, "orderId");
            String customerId    = extractStringValue(payload, "customerId");
            String correlationId = extractStringValue(payload, "correlationId");

            String message = "[" + topic.toUpperCase() + "] " + eventType
                    + (orderId    != null ? " | orderId="    + orderId    : "")
                    + (customerId != null ? " | customerId=" + customerId : "");

            LogEntry entry = LogEntry.builder()
                    .serviceName(defaultService)
                    .level(isFailure ? LogEntry.LogLevel.ERROR : LogEntry.LogLevel.INFO)
                    .message(message)
                    .traceId(correlationId)
                    .source(LogEntry.LogSource.KAFKA_EVENT)
                    .metadata(Map.of("topic", topic, "eventType", eventType))
                    .timestamp(Instant.now())
                    .build();

            logService.save(entry);
        } catch (Exception e) {
            log.warn("Failed to process {} record at offset {}: {}",
                    topic, record.offset(), e.getMessage());
        }
    }

    /**
     * Safely extracts a string value from a Map field.
     * Handles cases where UUID fields may be stored as String or nested Map.
     */
    private String extractStringValue(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null) return null;
        // UUID serialized as string
        if (val instanceof String s) return s.isBlank() ? null : s;
        // Some deserializers wrap UUIDs as maps - just use toString
        return val.toString();
    }

    private String extractEventType(ConsumerRecord<String, Object> record, Map<String, Object> payload) {
        // Try __TypeId__ Kafka header first — set by all producers via ADD_TYPE_INFO_HEADERS=true
        if (record.headers() != null) {
            var typeHeader = record.headers().lastHeader("__TypeId__");
            if (typeHeader != null) {
                String fullClass = new String(typeHeader.value());
                int dot = fullClass.lastIndexOf('.');
                String typeName = dot >= 0 ? fullClass.substring(dot + 1) : fullClass;
                log.debug("[LogConsumer] __TypeId__ header found: {} -> {}", fullClass, typeName);
                return typeName;
            }
        }
        // Fall back to payload field
        String fromPayload = getString(payload, "eventType", null);
        if (fromPayload != null) return fromPayload;

        log.debug("[LogConsumer] No __TypeId__ header and no eventType field in payload — keys: {}",
                payload.keySet());
        return "UnknownEvent";
    }

    private String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? val.toString() : defaultVal;
    }
}
