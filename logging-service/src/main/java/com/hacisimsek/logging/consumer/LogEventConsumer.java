package com.hacisimsek.logging.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.logging.model.LogEntry;
import com.hacisimsek.logging.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
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
 * Records arrive as raw bytes (headers on the wire include __TypeId__, which
 * Spring's JsonDeserializer would strip). We keep the headers and parse the JSON
 * payload into a Map ourselves — fully decoupled from other services' class
 * hierarchies, and able to report the real event type in each log entry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogEventConsumer {

    private final LogService logService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ── Explicit service log events ───────────────────────────────────────────

    @KafkaListener(topics = "service-logs", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeServiceLogs(ConsumerRecord<String, byte[]> record) {
        try {
            LogEntry entry = parseServiceLog(toPayload(record));
            logService.save(entry);
        } catch (Exception e) {
            log.warn("Failed to process service-logs record: {}", e.getMessage());
        }
    }

    // ── Business event topics — derived log entries ───────────────────────────

    @KafkaListener(topics = "order-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeOrderEvents(ConsumerRecord<String, byte[]> record) {
        saveEventLog("order-service", "order-events", record);
    }

    @KafkaListener(topics = "payment-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumePaymentEvents(ConsumerRecord<String, byte[]> record) {
        saveEventLog("payment-service", "payment-events", record);
    }

    @KafkaListener(topics = "inventory-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeInventoryEvents(ConsumerRecord<String, byte[]> record) {
        saveEventLog("inventory-service", "inventory-events", record);
    }

    @KafkaListener(topics = "shipping-events", groupId = "logging-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeShippingEvents(ConsumerRecord<String, byte[]> record) {
        saveEventLog("shipping-service", "shipping-events", record);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayload(ConsumerRecord<String, byte[]> record) {
        if (record.value() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(record.value(), Map.class);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot parse event payload", ex);
        }
    }

    /**
     * Parses an explicit service-log event.
     * Expected payload keys: serviceName, level, message, traceId, metadata, timestamp
     */
    private LogEntry parseServiceLog(Map<String, Object> payload) {

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
    private void saveEventLog(String defaultService, String topic, ConsumerRecord<String, byte[]> record) {
        try {
            Map<String, Object> payload = toPayload(record);

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
        if (val instanceof String s) return s.isBlank() ? null : s;
        return val.toString();
    }

    private String extractEventType(ConsumerRecord<String, byte[]> record, Map<String, Object> payload) {
        // Try __TypeId__ Kafka header first — set by all producers via ADD_TYPE_INFO_HEADERS=true
        if (record.headers() != null) {
            var typeHeader = record.headers().lastHeader("__TypeId__");
            if (typeHeader != null) {
                String fullClass = new String(typeHeader.value(), StandardCharsets.UTF_8);
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
