package com.hacisimsek.payment.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.payment.model.OrderCorrelation;
import com.hacisimsek.payment.repository.OrderCorrelationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Records the saga's correlation ID the moment an order is created, so that
 * client-triggered /initiate can resolve it synchronously even if the Kafka
 * InventoryReservedEvent (and therefore preparePayment) has not arrived yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCorrelationHandler {

    private static final String ORDER_CREATED = OrderCreatedEvent.class.getName();

    private final OrderCorrelationRepository correlationRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "order-events",
            groupId = "payment-correlation-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        if (!ORDER_CREATED.equals(type)) {
            return;
        }
        OrderCreatedEvent created = objectMapper.convertValue(event, OrderCreatedEvent.class);
        try {
            correlationRepository.save(OrderCorrelation.builder()
                    .orderId(created.getOrderId())
                    .correlationId(created.getCorrelationId())
                    .createdAt(Instant.now())
                    .build());
            log.debug("Recorded correlationId {} for order {}", created.getCorrelationId(), created.getOrderId());
        } catch (Exception e) {
            log.error("Failed to persist correlation for order {}: {}",
                    created.getOrderId(), e.getMessage());
        }
    }
}