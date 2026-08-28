package com.hacisimsek.inventory.service.impl;

import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryReservation;
import com.hacisimsek.inventory.model.InventoryStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.InventoryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ReservationRepository reservationRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final LogPublisher logPublisher;
    private final Counter reservationFailedCounter;

    private static final String SERVICE_NAME = "inventory-service";

    public InventoryServiceImpl(InventoryRepository inventoryRepository,
                                 ReservationRepository reservationRepository,
                                 KafkaTemplate<String, Object> kafkaTemplate,
                                 LogPublisher logPublisher,
                                 MeterRegistry meterRegistry) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.logPublisher = logPublisher;
        this.reservationFailedCounter = Counter.builder("zexxity.inventory.reservation.failed")
                .description("Total inventory reservation failures")
                .register(meterRegistry);
    }

    @Override
    public void reserveInventory(OrderCreatedEvent orderCreatedEvent) {
        log.info("Processing inventory reservation for order: {}", orderCreatedEvent.getOrderId());

        var existingReservation = reservationRepository.findByOrderId(orderCreatedEvent.getOrderId());
        if (existingReservation.isPresent()) {
            InventoryReservation reservation = existingReservation.get();
            if (reservation.getStatus() == InventoryReservation.ReservationStatus.CONFIRMED) {
                publishReservedEvent(orderCreatedEvent);
                log.info("Re-published inventory reservation event for order: {}", orderCreatedEvent.getOrderId());
                return;
            }
            throw new IllegalStateException(
                    "Inventory reservation is not in a retryable state for order: " + orderCreatedEvent.getOrderId());
        }

        List<InventoryReservation.ReservationItem> reservationItems = new ArrayList<>();
        Map<UUID, InventoryItem> inventoryByProductId = new LinkedHashMap<>();
        boolean allItemsAvailable = true;
        StringBuilder insufficientItemsMessage = new StringBuilder();

        for (var orderItem : orderCreatedEvent.getItems()) {
            InventoryItem item = inventoryRepository.findByProductId(orderItem.getProductId())
                    .orElse(null);

            if (item == null) {
                allItemsAvailable = false;
                insufficientItemsMessage.append("Product not found in inventory: ")
                        .append(orderItem.getProductId()).append("; ");
                continue;
            }

            int availableQuantity = item.getAvailableQuantity() == null ? 0 : item.getAvailableQuantity();
            if (availableQuantity < orderItem.getQuantity()) {
                allItemsAvailable = false;
                insufficientItemsMessage.append("Insufficient stock for product: ")
                        .append(orderItem.getProductId())
                        .append(" — requested: ").append(orderItem.getQuantity())
                        .append(", available: ").append(availableQuantity)
                        .append("; ");
                continue;
            }

            inventoryByProductId.put(orderItem.getProductId(), item);
            reservationItems.add(InventoryReservation.ReservationItem.builder()
                    .productId(orderItem.getProductId())
                    .quantity(orderItem.getQuantity())
                    .build());
        }

        if (!allItemsAvailable) {
            publishInventoryEvent(new InventoryReservationFailedEvent(
                    orderCreatedEvent.getCorrelationId(),
                    orderCreatedEvent.getOrderId(),
                    insufficientItemsMessage.toString()
            ));
            reservationFailedCounter.increment();
            log.error("Inventory reservation failed for order {}: {}", orderCreatedEvent.getOrderId(), insufficientItemsMessage);
            logPublisher.error(SERVICE_NAME,
                    orderCreatedEvent.getCorrelationId() != null ? orderCreatedEvent.getCorrelationId().toString() : null,
                    "Inventory reservation failed for order: " + orderCreatedEvent.getOrderId(),
                    Map.of("orderId", orderCreatedEvent.getOrderId().toString(),
                           "reason", insufficientItemsMessage.toString()));
            return;
        }

        // Save reservation
        InventoryReservation reservation = InventoryReservation.builder()
                .id(UUID.randomUUID())
                .orderId(orderCreatedEvent.getOrderId())
                .correlationId(orderCreatedEvent.getCorrelationId())
                .items(reservationItems)
                .status(InventoryReservation.ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        reservationRepository.save(reservation);

        // Deduct quantities and update status
        for (var reservationItem : reservationItems) {
            InventoryItem item = inventoryByProductId.get(reservationItem.getProductId());
            int newQty = item.getAvailableQuantity() - reservationItem.getQuantity();
            item.setAvailableQuantity(newQty);
            item.setReservedQuantity(item.getReservedQuantity() + reservationItem.getQuantity());
            item.setStatus(resolveStatus(newQty, item.getLowStockThreshold()));
            item.setUpdatedAt(LocalDateTime.now());
            inventoryRepository.save(item);

            // Publish low-stock alert if needed
            if (item.getStatus() == InventoryStatus.LOW_STOCK) {
                kafkaTemplate.send("inventory-alerts", Map.of(
                        "type",         "LOW_STOCK_ALERT",
                        "productId",    item.getProductId().toString(),
                        "availableQty", item.getAvailableQuantity(),
                        "threshold",    item.getLowStockThreshold()
                ));
                log.warn("LOW STOCK: product {} has only {} units left", item.getProductId(), newQty);
            }
        }

        reservation.setStatus(InventoryReservation.ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        publishReservedEvent(orderCreatedEvent);
        log.info("Inventory reserved for order: {}", orderCreatedEvent.getOrderId());
        logPublisher.info(SERVICE_NAME,
                orderCreatedEvent.getCorrelationId() != null ? orderCreatedEvent.getCorrelationId().toString() : null,
                "Inventory reserved for order: " + orderCreatedEvent.getOrderId(),
                Map.of("orderId", orderCreatedEvent.getOrderId().toString(),
                       "itemCount", String.valueOf(reservationItems.size())));
    }

    private void publishReservedEvent(OrderCreatedEvent orderCreatedEvent) {
        publishInventoryEvent(new InventoryReservedEvent(
                orderCreatedEvent.getCorrelationId(),
                orderCreatedEvent.getOrderId(),
                orderCreatedEvent.getCustomerId(),
                orderCreatedEvent.getCustomerEmail(),
                orderCreatedEvent.getTotalAmount()
        ));
    }

    @Override
    public void confirmReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));
        reservation.setStatus(InventoryReservation.ReservationStatus.CONFIRMED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("Reservation confirmed for order: {}", orderId);
    }

    @Override
    public void cancelReservation(UUID orderId) {
        InventoryReservation reservation = reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Reservation not found for order: " + orderId));

        if (reservation.getStatus() == InventoryReservation.ReservationStatus.CANCELLED) {
            log.info("Reservation already cancelled for order: {}", orderId);
            return;
        }

        // Return quantities to available
        for (var reservationItem : reservation.getItems()) {
            inventoryRepository.findByProductId(reservationItem.getProductId()).ifPresent(item -> {
                int returnedQty = item.getAvailableQuantity() + reservationItem.getQuantity();
                item.setAvailableQuantity(returnedQty);
                item.setReservedQuantity(Math.max(0, item.getReservedQuantity() - reservationItem.getQuantity()));
                item.setStatus(resolveStatus(returnedQty, item.getLowStockThreshold()));
                item.setUpdatedAt(LocalDateTime.now());
                inventoryRepository.save(item);
            });
        }

        reservation.setStatus(InventoryReservation.ReservationStatus.CANCELLED);
        reservation.setUpdatedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        log.info("Reservation cancelled and stock returned for order: {}", orderId);
    }

    private InventoryStatus resolveStatus(int qty, int threshold) {
        if (qty <= 0)         return InventoryStatus.OUT_OF_STOCK;
        if (qty <= threshold) return InventoryStatus.LOW_STOCK;
        return InventoryStatus.IN_STOCK;
    }

    private void publishInventoryEvent(Object event) {
        try {
            kafkaTemplate.send("inventory-events", event).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to publish " + event.getClass().getSimpleName() + " to inventory-events", ex);
        }
    }
}
