package com.hacisimsek.payment.service.impl;

import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.payment.PaymentFailedEvent;
import com.hacisimsek.common.event.payment.PaymentProcessedEvent;
import com.hacisimsek.payment.dto.GatewayOrderResponse;
import com.hacisimsek.payment.dto.InitiatePaymentRequest;
import com.hacisimsek.payment.dto.PaymentResponse;
import com.hacisimsek.payment.dto.RefundRequest;
import com.hacisimsek.payment.dto.VerifyPaymentRequest;
import com.hacisimsek.payment.gateway.GatewayOrderResult;
import com.hacisimsek.payment.gateway.PaymentGatewayAdapter;
import com.hacisimsek.payment.model.Payment;
import com.hacisimsek.payment.repository.PaymentRepository;
import com.hacisimsek.payment.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * All gateway adapters injected as a list — Spring discovers every PaymentGatewayAdapter bean.
     * We index them by their gateway type for O(1) lookup.
     */
    private final List<PaymentGatewayAdapter> gatewayAdapters;

    private final ObjectMapper objectMapper;

    private static final String PAYMENT_TOPIC = "payment-events";
    private static final String DEFAULT_CURRENCY = "INR";

    // ── Saga-driven auto processing (existing flow) ───────────────────────────

    @Override
    @Transactional
    public void processPayment(InventoryReservedEvent event) {
        log.info("Saga: processing payment for order={}", event.getOrderId());

        UUID orderId   = event.getOrderId();
        UUID customerId = event.getCustomerId();
        BigDecimal amount = event.getTotalAmount() != null ? event.getTotalAmount() : BigDecimal.valueOf(100);

        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .correlationId(event.getCorrelationId())
                .customerEmail(event.getCustomerEmail())
                .amount(amount)
                .status(Payment.PaymentStatus.PENDING)
                .gateway(Payment.PaymentGateway.MOCK)
                .paymentMethod("SAGA_AUTO")
                .build();

        paymentRepository.save(payment);

        // In saga mode, use the MOCK adapter (always succeeds in dev, configurable for testing)
        PaymentGatewayAdapter adapter = resolveAdapter(Payment.PaymentGateway.MOCK);
        GatewayOrderResult orderResult = adapter.createOrder(payment.getId(), amount, DEFAULT_CURRENCY);
        boolean success = adapter.verifyAndCapture(orderResult.getGatewayOrderId(), orderResult.getGatewayOrderId(), null);

        if (success) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setPaymentDate(Instant.now());
            payment.setGatewayOrderId(orderResult.getGatewayOrderId());
            payment.setTransactionId(generateTransactionId());
            paymentRepository.save(payment);

            kafkaTemplate.send(PAYMENT_TOPIC, new PaymentProcessedEvent(
                    event.getCorrelationId(), orderId, payment.getId(), customerId, event.getCustomerEmail()));
            log.info("Saga: payment completed for order={}, txn={}", orderId, payment.getTransactionId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("Saga payment processing failed");
            paymentRepository.save(payment);

            kafkaTemplate.send(PAYMENT_TOPIC, new PaymentFailedEvent(
                    event.getCorrelationId(), orderId, customerId,
                    event.getCustomerEmail(), "Payment processing failed"));
            log.error("Saga: payment failed for order={}", orderId);
        }
    }

    // ── Initiate (gateway order creation) ────────────────────────────────────

    @Override
    @Transactional
    public GatewayOrderResponse initiatePayment(InitiatePaymentRequest request) {
        Payment.PaymentGateway gateway = request.getGateway() != null
                ? request.getGateway() : Payment.PaymentGateway.RAZORPAY;

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .amount(request.getAmount())
                .status(Payment.PaymentStatus.PENDING)
                .gateway(gateway)
                .paymentMethod(request.getPaymentMethod())
                .build();

        payment = paymentRepository.save(payment);

        PaymentGatewayAdapter adapter = resolveAdapter(gateway);
        GatewayOrderResult result = adapter.createOrder(payment.getId(), request.getAmount(), DEFAULT_CURRENCY);

        payment.setGatewayOrderId(result.getGatewayOrderId());
        paymentRepository.save(payment);

        log.info("Payment initiated: id={}, gateway={}, gatewayOrderId={}",
                payment.getId(), gateway, result.getGatewayOrderId());

        return GatewayOrderResponse.builder()
                .paymentId(payment.getId())
                .gatewayOrderId(result.getGatewayOrderId())
                .gatewayKey(result.getPublicKey())
                .amount(request.getAmount())
                .currency(DEFAULT_CURRENCY)
                .status(Payment.PaymentStatus.PENDING.name())
                .build();
    }

    // ── Verify and Capture ────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentResponse verifyPayment(VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + request.getPaymentId()));

        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            log.warn("Payment {} already completed — returning existing record", payment.getId());
            return toResponse(payment);
        }

        PaymentGatewayAdapter adapter = resolveAdapter(payment.getGateway());
        boolean verified = adapter.verifyAndCapture(
                request.getGatewayPaymentId(), request.getGatewayOrderId(), request.getGatewaySignature());

        if (verified) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            payment.setGatewayPaymentId(request.getGatewayPaymentId());
            payment.setGatewaySignature(request.getGatewaySignature());
            payment.setTransactionId(generateTransactionId());
            payment.setPaymentDate(Instant.now());
            paymentRepository.save(payment);

            // Notify saga
            kafkaTemplate.send(PAYMENT_TOPIC, new PaymentProcessedEvent(
                    payment.getCorrelationId(), payment.getOrderId(), payment.getId(),
                    payment.getCustomerId(), payment.getCustomerEmail()));
            log.info("Payment verified and completed: id={}, txn={}", payment.getId(), payment.getTransactionId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("Gateway signature verification failed");
            paymentRepository.save(payment);

            kafkaTemplate.send(PAYMENT_TOPIC, new PaymentFailedEvent(
                    payment.getCorrelationId(), payment.getOrderId(),
                    payment.getCustomerId(), payment.getCustomerEmail(),
                    "Signature verification failed"));
            log.error("Payment verification failed: id={}", payment.getId());
        }

        return toResponse(payment);
    }

    // ── Refund ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PaymentResponse refundPayment(RefundRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + request.getPaymentId()));

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED
                && payment.getStatus() != Payment.PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Cannot refund payment in status: " + payment.getStatus());
        }

        if (payment.getGatewayPaymentId() == null) {
            throw new IllegalStateException("No gateway payment ID on record — cannot initiate refund");
        }

        BigDecimal refundAmount = request.getAmount() != null ? request.getAmount() : payment.getAmount();
        BigDecimal alreadyRefunded = payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO;
        BigDecimal newTotal = alreadyRefunded.add(refundAmount);

        if (newTotal.compareTo(payment.getAmount()) > 0) {
            throw new IllegalStateException(
                    "Refund amount " + newTotal + " exceeds original payment " + payment.getAmount());
        }

        PaymentGatewayAdapter adapter = resolveAdapter(payment.getGateway());
        String refundId = adapter.refund(payment.getGatewayPaymentId(), refundAmount);

        payment.setRefundedAmount(newTotal);
        payment.setRefundDate(Instant.now());
        payment.setStatus(newTotal.compareTo(payment.getAmount()) == 0
                ? Payment.PaymentStatus.REFUNDED
                : Payment.PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);

        log.info("Refund processed: paymentId={}, refundId={}, amount={}, status={}",
                payment.getId(), refundId, refundAmount, payment.getStatus());
        return toResponse(payment);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public PaymentResponse getPaymentById(UUID paymentId) {
        return toResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId)));
    }

    @Override
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        return toResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId)));
    }

    @Override
    public List<PaymentResponse> getPaymentsByCustomerId(UUID customerId) {
        return paymentRepository.findByCustomerId(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Webhook event handler ─────────────────────────────────────────────────

    /**
     * Called by WebhookController after signature has already been verified.
     *
     * Razorpay webhook payload structure:
     * {
     *   "event": "payment.captured",
     *   "payload": {
     *     "payment": {
     *       "entity": {
     *         "id": "pay_xxx",
     *         "order_id": "order_xxx",
     *         "amount": 50000,
     *         "error_description": "..."
     *       }
     *     },
     *     "refund": {
     *       "entity": { "id": "rfnd_xxx", "payment_id": "pay_xxx", "amount": 10000 }
     *     }
     *   }
     * }
     */
    @Override
    @Transactional
    public void handleWebhookEvent(String eventType, String webhookPayload) {
        log.info("[Webhook] Received Razorpay event: {}", eventType);

        try {
            JsonNode root = objectMapper.readTree(webhookPayload);

            switch (eventType) {

                case "payment.captured" -> {
                    JsonNode entity = root.path("payload").path("payment").path("entity");
                    String gatewayPaymentId = entity.path("id").asText();
                    String gatewayOrderId   = entity.path("order_id").asText();

                    paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresentOrElse(payment -> {
                        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                            log.info("[Webhook] payment.captured — already COMPLETED, skipping: {}", payment.getId());
                            return;
                        }
                        payment.setStatus(Payment.PaymentStatus.COMPLETED);
                        payment.setGatewayPaymentId(gatewayPaymentId);
                        payment.setTransactionId(generateTransactionId());
                        payment.setPaymentDate(Instant.now());
                        paymentRepository.save(payment);

                        kafkaTemplate.send(PAYMENT_TOPIC, new PaymentProcessedEvent(
                                payment.getCorrelationId(), payment.getOrderId(), payment.getId(),
                                payment.getCustomerId(), payment.getCustomerEmail()));

                        log.info("[Webhook] payment.captured → COMPLETED: paymentId={}, txn={}",
                                payment.getId(), payment.getTransactionId());

                    }, () -> log.warn("[Webhook] payment.captured — no payment found for gatewayOrderId={}", gatewayOrderId));
                }

                case "payment.failed" -> {
                    JsonNode entity = root.path("payload").path("payment").path("entity");
                    String gatewayOrderId  = entity.path("order_id").asText();
                    String errorDesc       = entity.path("error_description").asText("Payment failed at gateway");

                    paymentRepository.findByGatewayOrderId(gatewayOrderId).ifPresentOrElse(payment -> {
                        if (payment.getStatus() == Payment.PaymentStatus.FAILED) {
                            log.info("[Webhook] payment.failed — already FAILED, skipping: {}", payment.getId());
                            return;
                        }
                        payment.setStatus(Payment.PaymentStatus.FAILED);
                        payment.setFailureReason(errorDesc);
                        paymentRepository.save(payment);

                        kafkaTemplate.send(PAYMENT_TOPIC, new PaymentFailedEvent(
                                payment.getCorrelationId(), payment.getOrderId(),
                                payment.getCustomerId(), payment.getCustomerEmail(), errorDesc));

                        log.error("[Webhook] payment.failed → FAILED: paymentId={}, reason={}",
                                payment.getId(), errorDesc);

                    }, () -> log.warn("[Webhook] payment.failed — no payment found for gatewayOrderId={}", gatewayOrderId));
                }

                case "refund.created" -> {
                    JsonNode entity          = root.path("payload").path("refund").path("entity");
                    String gatewayPaymentId  = entity.path("payment_id").asText();
                    long   refundPaise       = entity.path("amount").asLong(0);
                    BigDecimal refundAmount  = BigDecimal.valueOf(refundPaise).divide(BigDecimal.valueOf(100));

                    paymentRepository.findByGatewayPaymentId(gatewayPaymentId).ifPresentOrElse(payment -> {
                        BigDecimal alreadyRefunded = payment.getRefundedAmount() != null
                                ? payment.getRefundedAmount() : BigDecimal.ZERO;
                        BigDecimal newTotal = alreadyRefunded.add(refundAmount);

                        payment.setRefundedAmount(newTotal);
                        payment.setRefundDate(Instant.now());
                        payment.setStatus(newTotal.compareTo(payment.getAmount()) >= 0
                                ? Payment.PaymentStatus.REFUNDED
                                : Payment.PaymentStatus.PARTIALLY_REFUNDED);
                        paymentRepository.save(payment);

                        log.info("[Webhook] refund.created → {}: paymentId={}, refundAmount={}",
                                payment.getStatus(), payment.getId(), refundAmount);

                    }, () -> log.warn("[Webhook] refund.created — no payment found for gatewayPaymentId={}", gatewayPaymentId));
                }

                default -> log.info("[Webhook] Unhandled event type: {} — ignoring", eventType);
            }

        } catch (Exception e) {
            log.error("[Webhook] Failed to process event {}: {}", eventType, e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed for event: " + eventType, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PaymentGatewayAdapter resolveAdapter(Payment.PaymentGateway gateway) {
        Map<Payment.PaymentGateway, PaymentGatewayAdapter> index = gatewayAdapters.stream()
                .collect(Collectors.toMap(PaymentGatewayAdapter::getGateway, Function.identity()));
        PaymentGatewayAdapter adapter = index.get(gateway);
        if (adapter == null) {
            // Fallback to MOCK so the service never hard-crashes during development
            adapter = index.get(Payment.PaymentGateway.MOCK);
            if (adapter == null) throw new IllegalStateException("No gateway adapter found for: " + gateway);
        }
        return adapter;
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .orderId(p.getOrderId())
                .customerId(p.getCustomerId())
                .amount(p.getAmount())
                .refundedAmount(p.getRefundedAmount())
                .status(p.getStatus())
                .gateway(p.getGateway())
                .paymentMethod(p.getPaymentMethod())
                .gatewayPaymentId(p.getGatewayPaymentId())
                .transactionId(p.getTransactionId())
                .failureReason(p.getFailureReason())
                .paymentDate(p.getPaymentDate())
                .refundDate(p.getRefundDate())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
