package com.hacisimsek.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String customerEmail;

    private UUID customerId;
    private BigDecimal totalAmount;

    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    private List<OrderItem> items;

    @Column(updatable = false)
    private Instant createdAt;
    private String createdBy;
    private Instant lastModifiedAt;
    private String lastModifiedBy;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.lastModifiedAt = now;
        this.createdBy = "simsekhaciI";
        this.lastModifiedBy = "simsekhaciI";
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = Instant.now();
        this.lastModifiedBy = "simsekhaciI";
    }

    public enum OrderStatus {
        PENDING,
        INVENTORY_CHECKING,
        INVENTORY_RESERVED,
        PAYMENT_PROCESSING,
        PAYMENT_COMPLETED,
        SHIPPING_PROCESSING,
        SHIPPED,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}
