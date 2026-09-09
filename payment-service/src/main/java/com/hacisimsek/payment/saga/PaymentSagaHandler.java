package com.hacisimsek.payment.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentRefundedEvent;
import com.hacisimsek.common.event.shipping.ShipmentFailedEvent;
import com.hacisimsek.payment.dto.RefundRequest;
import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.repository.PaymentRepository;
import com.hacisimsek.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaHandler {

    private static final String INVENTORY_RESERVED = InventoryReservedEvent.class.getName();
    private static final String SHIPMENT_FAILED    = ShipmentFailedEvent.class.getName();

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String COMPENSATION_TOPIC = "compensation-events";

    /**
     * When false: saga stops at INVENTORY_RESERVED and waits for the customer
     * to manually initiate payment via /api/v1/payments/initiate (Razorpay flow).
     * When true: saga auto-processes via MOCK immediately (testing/demo mode).
     */
    @Value("${payment.saga-auto-process:false}")
    private boolean sagaAutoProcess;

    // ── Forward: reserve inventory → initiate payment ─────────────────────────

    @KafkaListener(
            topics = "inventory-events",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleInventoryEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        if (INVENTORY_RESERVED.equals(type)) {
            InventoryReservedEvent inventoryReservedEvent =
                    objectMapper.convertValue(event, InventoryReservedEvent.class);
            if (sagaAutoProcess) {
                log.info("saga-auto-process=true — auto-processing payment for order: {}",
                        inventoryReservedEvent.getOrderId());
                paymentService.processPayment(inventoryReservedEvent);
            } else {
                log.info("saga-auto-process=false — preparing manual payment for order: {}. " +
                        "Call POST /api/v1/payments/initiate to proceed.",
                        inventoryReservedEvent.getOrderId());
                paymentService.preparePayment(inventoryReservedEvent);
            }
        } else {
            log.debug("Ignoring non-InventoryReservedEvent on inventory-events: {}", type);
        }
    }

    // ── Compensation: shipment failed → auto-refund ───────────────────────────
    //
    // When shipping fails AFTER a successful payment, money has already been
    // captured. We must automatically issue a full refund so the customer
    // gets their money back without having to contact support.

    @KafkaListener(
            topics = "shipping-events",
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleShippingEvents(ConsumerRecord<String, Object> record) {
        Object event = record.value();
        String type = event != null ? event.getClass().getName() : null;
        if (SHIPMENT_FAILED.equals(type)) {
            ShipmentFailedEvent shipmentFailedEvent =
                    objectMapper.convertValue(event, ShipmentFailedEvent.class);
            log.warn("ShipmentFailedEvent received for order: {} — initiating auto-refund",
                    shipmentFailedEvent.getOrderId());
            processAutoRefund(shipmentFailedEvent);
        }
        // ShipmentProcessedEvent is ignored — no action needed on success
    }

    private void processAutoRefund(ShipmentFailedEvent event) {
        paymentRepository.findByOrderId(event.getOrderId()).ifPresentOrElse(payment -> {
            // Only refund if payment was actually completed
            if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
                log.info("Skipping auto-refund for order {} — payment status is {}",
                        event.getOrderId(), payment.getStatus());
                return;
            }

            try {
                log.info("Auto-refunding payment {} for order {} — shipment failed: {}",
                        payment.getId(), event.getOrderId(), event.getReason());

                RefundRequest refundRequest = new RefundRequest();
                refundRequest.setPaymentId(payment.getId());
                refundRequest.setAmount(payment.getAmount()); // full refund
                refundRequest.setReason("Shipment failed: " + event.getReason());

                paymentService.refundPayment(refundRequest);

                log.info("Auto-refund completed for order {} — payment {} refunded",
                        event.getOrderId(), payment.getId());

                // Notify the saga so order-service can record PAYMENT_REFUNDED
                // in the order event log with a truthful previousStatus.
                try {
                    PaymentRefundedEvent refundedEvent = new PaymentRefundedEvent(
                            event.getCorrelationId(),
                            event.getOrderId(),
                            payment.getId(),
                            payment.getCustomerId(),
                            payment.getCustomerEmail(),
                            refundRequest.getReason());
                    kafkaTemplate.send(COMPENSATION_TOPIC, refundedEvent);
                    log.info("PaymentRefundedEvent published for order {} on {}",
                            event.getOrderId(), COMPENSATION_TOPIC);
                } catch (Exception publishEx) {
                    // Refund already succeeded — log only, never roll it back
                    log.error("Failed to publish PaymentRefundedEvent for order {}: {}",
                            event.getOrderId(), publishEx.getMessage());
                }

            } catch (Exception e) {
                log.error("Auto-refund FAILED for order {} payment {}: {}",
                        event.getOrderId(), payment.getId(), e.getMessage());
                // In production: alert ops team, queue for manual processing
            }
        }, () -> log.warn("Auto-refund skipped — no payment found for order: {}", event.getOrderId()));
    }
}
