package com.hacisimsek.payment.repository;

import com.hacisimsek.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    List<Payment> findByCustomerId(UUID customerId);

    List<Payment> findByStatus(Payment.PaymentStatus status);

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
}
