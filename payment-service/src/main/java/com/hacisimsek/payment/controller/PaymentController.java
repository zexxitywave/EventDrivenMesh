package com.hacisimsek.payment.controller;

import com.hacisimsek.payment.dto.GatewayOrderResponse;
import com.hacisimsek.payment.dto.InitiatePaymentRequest;
import com.hacisimsek.payment.dto.PaymentResponse;
import com.hacisimsek.payment.dto.RefundRequest;
import com.hacisimsek.payment.dto.VerifyPaymentRequest;
import com.hacisimsek.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final RestTemplate restTemplate;

    /** Initiate a payment â€” returns gateway order/session for frontend checkout UI. */
    @PostMapping("/initiate")
    public ResponseEntity<GatewayOrderResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        return ResponseEntity.ok(paymentService.initiatePayment(request));
    }

    /** Verify gateway callback and capture the payment. */
    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    /** Initiate a full or partial refund. */
    @PostMapping("/refund")
    public ResponseEntity<PaymentResponse> refundPayment(
            @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.ok(paymentService.refundPayment(request));
    }

    /** Get payment by internal payment ID. */
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPaymentById(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(paymentService.getPaymentById(paymentId));
    }

    /** Get payment by order ID. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrderId(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId));
    }

    /** Get all payments for a customer. */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByCustomerId(
            @PathVariable UUID customerId) {
        return ResponseEntity.ok(paymentService.getPaymentsByCustomerId(customerId));
    }

    /**
     * Proxy endpoint used by the checkout UI.
     * Fetches order details from order-service via Eureka load balancing
     * so the browser never needs to cross origins or deal with JWT headers.
     */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map> getOrderForCheckout(@PathVariable UUID orderId) {
        try {
            String url = "http://order-service/api/orders/" + orderId;
            Map order = restTemplate.getForObject(url, Map.class);
            return ResponseEntity.ok(order);
        } catch (HttpClientErrorException.NotFound e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to fetch order {} from order-service: {}", orderId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
