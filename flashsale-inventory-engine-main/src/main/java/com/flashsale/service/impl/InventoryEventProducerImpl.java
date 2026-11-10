package com.flashsale.service.impl;

import com.flashsale.event.InventoryReservationEvent;
import com.flashsale.service.InventoryEventProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryEventProducerImpl
        implements InventoryEventProducer {

    private static final String TOPIC =
            "inventory-reservations";

    private final KafkaTemplate<String, InventoryReservationEvent>
            kafkaTemplate;

    public InventoryEventProducerImpl(
            KafkaTemplate<String, InventoryReservationEvent> kafkaTemplate) {

        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishReservation(
            InventoryReservationEvent event) {

        kafkaTemplate.send(
                TOPIC,
                String.valueOf(event.productId()),
                event
        );
    }
}
