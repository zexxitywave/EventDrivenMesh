package com.hacisimsek.analytics.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.analytics.model.OrderAnalytics;
import com.hacisimsek.analytics.repository.OrderAnalyticsRepository;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumes order-events from Kafka and persists them to PostgreSQL analytics_db.
 *
 * Three production patterns implemented:
 *
 * 1. IDEMPOTENCY — unique constraint on event_id prevents duplicate rows
 *    even if the same message is consumed twice (e.g. after a consumer restart).
 *
 * 2. POISON PILL detection — a message that cannot be deserialized or is
 *    structurally invalid (missing orderId, wrong types) would crash the consumer
 *    in an infinite retry loop. We catch these individually and route them to DLQ
 *    so the rest of the batch continues processing.
 *
 * 3. DLQ (Dead Letter Queue) — poison pills and unprocessable messages are
 *    published to 'order-analytics-dlq' topic instead of being silently dropped.
 *    This preserves the data for inspection and replay after fixing the root cause.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAnalyticsConsumer {

    private final OrderAnalyticsRepository repository;
    private final KafkaTemplate<String, byte[]> dlqKafkaTemplate;

    @Value("${analytics.dlq-topic:order-analytics-dlq}")
    private String dlqTopic;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(
        topics = "order-events",
        groupId = "analytics-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(List<ConsumerRecord<String, byte[]>> records) {
        List<OrderAnalytics> batch = new ArrayList<>(records.size());
        int poisonPills = 0;
        int duplicates = 0;

        for (ConsumerRecord<String, byte[]> record : records) {
            try {
                // ── Deserialize ────────────────────────────────────────────────
                OrderCreatedEvent e = objectMapper.readValue(record.value(), OrderCreatedEvent.class);

                // ── Poison pill check ──────────────────────────────────────────
                // A message missing orderId is structurally invalid — it would
                // cause every downstream query to fail. Route to DLQ immediately.
                if (e.getOrderId() == null) {
                    log.warn("[Analytics] Poison pill detected at offset {} — missing orderId. Routing to DLQ.",
                            record.offset());
                    sendToDlq(record, "missing_order_id");
                    poisonPills++;
                    continue;
                }

                batch.add(OrderAnalytics.builder()
                        .eventId(e.getEventId())
                        .correlationId(e.getCorrelationId())
                        .eventTimestamp(e.getTimestamp())
                        .orderId(e.getOrderId())
                        .customerId(e.getCustomerId())
                        .customerEmail(e.getCustomerEmail())
                        .totalAmount(e.getTotalAmount())
                        .itemCount(e.getItems() != null ? e.getItems().size() : 0)
                        .ingestedAt(Instant.now())
                        .build());

            } catch (Exception ex) {
                // ── Poison pill — deserialization failure ──────────────────────
                // Corrupted JSON, wrong field types, schema mismatch.
                // Without DLQ this would block the consumer forever.
                log.warn("[Analytics] Poison pill at offset {} partition {} — {}. Routing to DLQ.",
                        record.offset(), record.partition(), ex.getMessage());
                sendToDlq(record, ex.getMessage());
                poisonPills++;
            }
        }

        if (!batch.isEmpty()) {
            try {
                // ── Batch insert ───────────────────────────────────────────────
                repository.saveAll(batch);
                log.info("[Analytics] Batch saved {} events | poison_pills={} | duplicates={}",
                        batch.size(), poisonPills, duplicates);

            } catch (DataIntegrityViolationException ex) {
                // ── Idempotency — duplicate event_id ──────────────────────────
                // Happens when consumer restarts and re-reads already-processed messages.
                // The unique constraint on event_id rejects duplicates at DB level.
                // Fall back to one-by-one inserts, skipping duplicates gracefully.
                log.warn("[Analytics] Duplicate events detected in batch — falling back to upsert mode");
                for (OrderAnalytics record : batch) {
                    try {
                        repository.save(record);
                    } catch (DataIntegrityViolationException dup) {
                        log.debug("[Analytics] Skipping duplicate event_id: {}", record.getEventId());
                        duplicates++;
                    }
                }
                log.info("[Analytics] Upsert complete — saved={} duplicates_skipped={}",
                        batch.size() - duplicates, duplicates);
            }
        }

        if (poisonPills > 0) {
            log.warn("[Analytics] {} poison pill(s) sent to DLQ topic '{}'", poisonPills, dlqTopic);
        }
    }

    /**
     * Sends an unprocessable message to the Dead Letter Queue.
     * The original raw bytes are preserved so the message can be
     * inspected and replayed after fixing the root cause.
     */
    private void sendToDlq(ConsumerRecord<String, byte[]> record, String reason) {
        try {
            dlqKafkaTemplate.send(dlqTopic, record.key(), record.value());
            log.info("[Analytics] Sent to DLQ — topic={} offset={} reason={}",
                    dlqTopic, record.offset(), reason);
        } catch (Exception ex) {
            log.error("[Analytics] Failed to send to DLQ — message will be lost! offset={} error={}",
                    record.offset(), ex.getMessage());
        }
    }
}

//POST /api/orders
//      ↓
//OrderController
//      ↓
//OrderService
//      ↓
//OrderRepository
//      ↓
//PostgreSQL
//      ↓
//Kafka Producer
