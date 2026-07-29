package com.hacisimsek.analytics.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.analytics.model.OrderAnalytics;
import com.hacisimsek.analytics.repository.OrderAnalyticsRepository;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAnalyticsConsumer {

    private final OrderAnalyticsRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(
        topics = "order-events",
        groupId = "analytics-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, byte[]> record) {
        try {
            OrderCreatedEvent e = objectMapper.readValue(record.value(), OrderCreatedEvent.class);

            // skip if already saved (idempotency check)
            if (e.getOrderId() == null) return;

            OrderAnalytics analytics = OrderAnalytics.builder()
                    .eventId(e.getEventId())
                    .correlationId(e.getCorrelationId())
                    .eventTimestamp(e.getTimestamp())
                    .orderId(e.getOrderId())
                    .customerId(e.getCustomerId())
                    .customerEmail(e.getCustomerEmail())
                    .totalAmount(e.getTotalAmount())
                    .itemCount(e.getItems() != null ? e.getItems().size() : 0)
                    .ingestedAt(Instant.now())
                    .build();

            repository.save(analytics);
            log.info("[Analytics] Saved order {} — amount: {}", e.getOrderId(), e.getTotalAmount());

        } catch (Exception ex) {
            log.warn("[Analytics] Skipping unprocessable message at offset {}: {}",
                    record.offset(), ex.getMessage());
        }
    }
}
