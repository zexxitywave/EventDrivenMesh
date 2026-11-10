package com.flashsale.repository;

import com.flashsale.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByOrderIdAndStatusNot(Long orderId, com.flashsale.entity.PaymentStatus status);
}
