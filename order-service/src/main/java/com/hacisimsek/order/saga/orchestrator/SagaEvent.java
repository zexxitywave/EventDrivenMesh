package com.hacisimsek.order.saga.orchestrator;

/**
 * Events that drive the Order Saga state machine.
 * Published by downstream services and received via Kafka listeners.
 */
public enum SagaEvent {
    ORDER_PLACED,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    SHIPMENT_CREATED,
    SHIPMENT_FAILED,
    DELIVERY_CONFIRMED
}
