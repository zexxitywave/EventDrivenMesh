package com.hacisimsek.order.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderReadModelRepository extends JpaRepository<OrderReadModel, UUID> {

    List<OrderReadModel> findByCustomerId(UUID customerId);
}