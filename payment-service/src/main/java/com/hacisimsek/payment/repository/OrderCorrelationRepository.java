package com.hacisimsek.payment.repository;

import com.hacisimsek.payment.model.OrderCorrelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderCorrelationRepository extends JpaRepository<OrderCorrelation, UUID> {

    Optional<OrderCorrelation> findByOrderId(UUID orderId);
}