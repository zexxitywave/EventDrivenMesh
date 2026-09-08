package com.hacisimsek.payment.saga;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaHandler {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

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
        if (event instanceof InventoryReservedEvent inventoryReservedEvent) {
            if (sagaAutoProcess) {
                log.info("saga-auto-process=true — auto-processing payment for order: {}",
                        inventoryReservedEvent.getOrderId());
                paymentService.processPayment(inventoryReservedEvent);
            } else {
                log.info("saga-auto-process=false — waiting for manual payment for order: {}. " +
                        "Call POST /api/v1/payments/initiate to proceed.",
                        inventoryReservedEvent.getOrderId());
                // Do nothing — customer will initiate payment manually via Razorpay
            }
        } else {
            log.debug("Ignoring non-InventoryReservedEvent on inventory-events: {}",
                    event != null ? event.getClass().getSimpleName() : "null");
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
        if (event instanceof ShipmentFailedEvent shipmentFailedEvent) {
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

            } catch (Exception e) {
                log.error("Auto-refund FAILED for order {} payment {}: {}",
                        event.getOrderId(), payment.getId(), e.getMessage());
                // In production: alert ops team, queue for manual processing
            }
        }, () -> log.warn("Auto-refund skipped — no payment found for order: {}", event.getOrderId()));
    }
}
