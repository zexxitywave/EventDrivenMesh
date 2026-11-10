
package com.flashsale.service;

import com.flashsale.entity.Inventory;
import com.flashsale.entity.ProcessedEvent;
import com.flashsale.event.InventoryReservationEvent;
import com.flashsale.repository.ProcessedEventRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryEventConsumer {

    private final InventoryService inventoryService;
    private final ProcessedEventRepository processedEventRepository;
    private final MetricsService metricsService;

    public InventoryEventConsumer(
            InventoryService inventoryService,
            ProcessedEventRepository processedEventRepository,
            MetricsService metricsService) {

        this.inventoryService = inventoryService;
        this.processedEventRepository = processedEventRepository;
        this.metricsService = metricsService;
    }
    @Transactional
    @KafkaListener(
            topics = "inventory-reservations",
            groupId = "flashsale-inventory"
    )
    public void consumeReservation(
            InventoryReservationEvent event) {

        System.out.println("================================");
        System.out.println("SPRING KAFKA CONSUMER RECEIVED");
        System.out.println("Event: " + event);
        System.out.println("Event ID: " + event.eventId());
        System.out.println("Product ID: " + event.productId());
        System.out.println("Quantity: " + event.quantity());

        // Check whether this event was already processed
        if (processedEventRepository.existsById(event.eventId())) {

            metricsService.kafkaDuplicate();
            System.out.println(
                    "DUPLICATE EVENT - SKIPPING"
            );

            System.out.println("================================");

            return;
        }

        // Actually reserve the inventory
        Inventory inventory = inventoryService.reserveStock(
                event.productId(),
                event.quantity()
        );

        // Mark event as processed only after successful reservation
        processedEventRepository.save(
                new ProcessedEvent(event.eventId())
        );

        metricsService.kafkaProcessed();

        System.out.println("INVENTORY RESERVED SUCCESSFULLY");

        System.out.println(
                "Available Stock: " +
                        inventory.getAvailableQuantity()
        );

        System.out.println(
                "Reserved Stock: " +
                        inventory.getReservedQuantity()
        );

        System.out.println("================================");
    }
}

