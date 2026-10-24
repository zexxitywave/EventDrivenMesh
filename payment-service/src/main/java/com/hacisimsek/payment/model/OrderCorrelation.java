package com.hacisimsek.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Sink for the saga's correlation ID. Persisted the moment payment-service
 * observes OrderCreatedEvent, so a client-triggered /initiate can always
 * resolve the correlation synchronously — no race with the Kafka-driven
 * preparePayment() (which reads InventoryReservedEvent).
 */
@Entity
@Table(name = "order_correlations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCorrelation {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private UUID correlationId;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}