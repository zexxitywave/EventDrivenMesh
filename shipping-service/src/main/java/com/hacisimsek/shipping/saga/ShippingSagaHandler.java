package com.hacisimsek.shipping.saga;
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

    private final ShippingService shippingService;
    // Shipping is triggered by a successful payment.

    @KafkaListener(
            topics = "payment-events",
            groupId = "shipping-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvents(ConsumerRecord<String, Object> record) {

        Object event = record.value();

        log.info("Received event class: {}", event.getClass().getName());

        if (event instanceof PaymentProcessedEvent paymentProcessedEvent) {

            log.info("Received PaymentProcessedEvent for order: {}",
                    paymentProcessedEvent.getOrderId());

            shippingService.processShipping(paymentProcessedEvent);
        }
    }
}