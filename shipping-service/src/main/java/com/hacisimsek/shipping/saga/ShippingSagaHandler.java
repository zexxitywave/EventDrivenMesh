package com.hacisimsek.shipping.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingSagaHandler {

    private static final String PAYMENT_PROCESSED = PaymentProcessedEvent.class.getName();

    private final ShippingService shippingService;
    private final ObjectMapper objectMapper;

    // Shipping is triggered by a successful payment.

    @KafkaListener(
            topics = "payment-events",
            groupId = "shipping-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        log.info("[ShippingSagaHandler] payment-events type={}", type);

        if (PAYMENT_PROCESSED.equals(type)) {
            PaymentProcessedEvent paymentProcessedEvent =
                    objectMapper.convertValue(event, PaymentProcessedEvent.class);
            log.info("Received PaymentProcessedEvent for order: {}",
                    paymentProcessedEvent.getOrderId());
            shippingService.processShipping(paymentProcessedEvent);
        } else {
            log.debug("[ShippingSagaHandler] Ignoring non-PaymentProcessedEvent on payment-events: {}", type);
        }
    }
}