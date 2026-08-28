package com.hacisimsek.shipping.service.impl;

import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.common.event.shipping.ShipmentProcessedEvent;
import com.hacisimsek.common.logging.LogPublisher;
import com.hacisimsek.shipping.model.Shipment;
import com.hacisimsek.shipping.repository.ShipmentRepository;
import com.hacisimsek.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingServiceImpl implements ShippingService {

    private final ShipmentRepository shipmentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final LogPublisher logPublisher;

    private static final String SERVICE_NAME = "shipping-service";
    private static final String[] CARRIERS = {"DHL", "FedEx", "UPS", "USPS"};

    @Override
    @Transactional
    public void processShipping(PaymentProcessedEvent paymentEvent) {
        log.info("Processing shipping for order: {}", paymentEvent.getOrderId());

        // In a real application, we would retrieve shipping address from order service
        // For this example, we'll create a shipment with minimal information

        try {
            // Create the shipment record
            Shipment shipment = Shipment.builder()
                    .orderId(paymentEvent.getOrderId())
                    .customerId(paymentEvent.getCustomerId() != null ? paymentEvent.getCustomerId() : UUID.randomUUID())
                    .correlationId(paymentEvent.getCorrelationId())
                    .status(Shipment.ShipmentStatus.PROCESSING)
                    .carrierName(getRandomCarrier())
                    .trackingNumber(generateTrackingNumber())
                    .shippedDate(Instant.now())
                    .estimatedDeliveryDate(Instant.now().plus(3, ChronoUnit.DAYS))
                    // Minimal shipping details for demo
                    .shippingAddress("123 Main St, New York, NY 10001")
                    .recipientName("John Doe")
                    .recipientPhone("(212) 555-1234")
                    .build();

            Shipment savedShipment = shipmentRepository.save(shipment);

            // Update shipment status to shipped
            savedShipment.setStatus(Shipment.ShipmentStatus.SHIPPED);
            shipmentRepository.save(savedShipment);

            // Send shipment processed event
            ShipmentProcessedEvent shipmentEvent = new ShipmentProcessedEvent(
                    paymentEvent.getCorrelationId(),
                    paymentEvent.getOrderId(),
                    savedShipment.getId(),
                    savedShipment.getCustomerId(),
                    paymentEvent.getCustomerEmail(),
                    savedShipment.getTrackingNumber()
            );

            kafkaTemplate.send("shipping-events", shipmentEvent);
            log.info("Order shipped successfully. Order ID: {}, Tracking: {}",
                    paymentEvent.getOrderId(), savedShipment.getTrackingNumber());
            logPublisher.info(SERVICE_NAME,
                    paymentEvent.getCorrelationId() != null ? paymentEvent.getCorrelationId().toString() : null,
                    "Shipment created for order: " + paymentEvent.getOrderId() + " | tracking: " + savedShipment.getTrackingNumber(),
                    Map.of("orderId", paymentEvent.getOrderId().toString(),
                           "shipmentId", savedShipment.getId().toString(),
                           "trackingNumber", savedShipment.getTrackingNumber(),
                           "carrier", savedShipment.getCarrierName()));

        } catch (Exception e) {
            log.error("Failed to process shipping for order: {}", paymentEvent.getOrderId(), e);
            logPublisher.error(SERVICE_NAME,
                    paymentEvent.getCorrelationId() != null ? paymentEvent.getCorrelationId().toString() : null,
                    "Shipment failed for order: " + paymentEvent.getOrderId(),
                    e,
                    Map.of("orderId", paymentEvent.getOrderId().toString(),
                           "reason", e.getMessage() != null ? e.getMessage() : "unknown"));

            ShipmentFailedEvent failedEvent = new ShipmentFailedEvent(
                    paymentEvent.getCorrelationId(),
                    paymentEvent.getOrderId(),
                    "Failed to process shipment: " + e.getMessage()
            );

            kafkaTemplate.send("shipping-events", failedEvent);
        }
    }

    @Override
    public Shipment getShipmentByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Shipment not found for order: " + orderId));
    }

    @Override
    public Shipment getShipmentById(UUID shipmentId) {
        return shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new RuntimeException("Shipment not found with ID: " + shipmentId));
    }

    private String getRandomCarrier() {
        return CARRIERS[new Random().nextInt(CARRIERS.length)];
    }

    private String generateTrackingNumber() {
        // Format: 2 letters + 9 digits + 2 letters
        String letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder();

        Random random = new Random();

        // First 2 letters
        for (int i = 0; i < 2; i++) {
            sb.append(letters.charAt(random.nextInt(letters.length())));
        }

        // 9 digits
        for (int i = 0; i < 9; i++) {
            sb.append(random.nextInt(10));
        }

        // Last 2 letters
        for (int i = 0; i < 2; i++) {
            sb.append(letters.charAt(random.nextInt(letters.length())));
        }

        return sb.toString();
    }
}
