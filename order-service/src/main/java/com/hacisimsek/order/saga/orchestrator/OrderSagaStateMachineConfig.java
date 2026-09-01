package com.hacisimsek.order.saga.orchestrator;

import com.hacisimsek.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

/**
 * Spring State Machine configuration for the Order Saga Orchestrator.
 *
 * This replaces the scattered choreography listeners with a single, explicit
 * state machine that drives the entire order lifecycle from one place.
 *
 * States map 1:1 with Order.OrderStatus.
 * Events (SagaEvent) represent outcomes from downstream services.
 *
 * Transitions:
 *
 *   PENDING ──[ORDER_PLACED]──► INVENTORY_CHECKING
 *   INVENTORY_CHECKING ──[INVENTORY_RESERVED]──► PAYMENT_PROCESSING
 *   INVENTORY_CHECKING ──[INVENTORY_FAILED]──► CANCELLED
 *   PAYMENT_PROCESSING ──[PAYMENT_COMPLETED]──► SHIPPING_PROCESSING
 *   PAYMENT_PROCESSING ──[PAYMENT_FAILED]──► FAILED (+ compensate inventory)
 *   SHIPPING_PROCESSING ──[SHIPMENT_CREATED]──► SHIPPED
 *   SHIPPING_PROCESSING ──[SHIPMENT_FAILED]──► FAILED (+ compensate inventory)
 *   SHIPPED ──[DELIVERY_CONFIRMED]──► COMPLETED
 *
 * The factory produces one StateMachine per order (keyed by orderId).
 */
@Configuration
@EnableStateMachineFactory
@Slf4j
public class OrderSagaStateMachineConfig
        extends StateMachineConfigurerAdapter<Order.OrderStatus, SagaEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<Order.OrderStatus, SagaEvent> states)
            throws Exception {
        states
            .withStates()
                .initial(Order.OrderStatus.PENDING)
                .states(EnumSet.allOf(Order.OrderStatus.class))
                .end(Order.OrderStatus.COMPLETED)
                .end(Order.OrderStatus.CANCELLED)
                .end(Order.OrderStatus.FAILED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<Order.OrderStatus, SagaEvent> transitions)
            throws Exception {
        transitions
            // Order placed → start inventory check
            .withExternal()
                .source(Order.OrderStatus.PENDING)
                .target(Order.OrderStatus.INVENTORY_CHECKING)
                .event(SagaEvent.ORDER_PLACED)
                .and()

            // Inventory reserved → initiate payment
            .withExternal()
                .source(Order.OrderStatus.INVENTORY_CHECKING)
                .target(Order.OrderStatus.INVENTORY_RESERVED)
                .event(SagaEvent.INVENTORY_RESERVED)
                .and()

            .withExternal()
                .source(Order.OrderStatus.INVENTORY_RESERVED)
                .target(Order.OrderStatus.PAYMENT_PROCESSING)
                .event(SagaEvent.INVENTORY_RESERVED)
                .and()

            // Inventory failed → cancel order (no compensation needed)
            .withExternal()
                .source(Order.OrderStatus.INVENTORY_CHECKING)
                .target(Order.OrderStatus.CANCELLED)
                .event(SagaEvent.INVENTORY_FAILED)
                .and()

            // Payment completed → initiate shipping
            .withExternal()
                .source(Order.OrderStatus.PAYMENT_PROCESSING)
                .target(Order.OrderStatus.PAYMENT_COMPLETED)
                .event(SagaEvent.PAYMENT_COMPLETED)
                .and()

            .withExternal()
                .source(Order.OrderStatus.PAYMENT_COMPLETED)
                .target(Order.OrderStatus.SHIPPING_PROCESSING)
                .event(SagaEvent.PAYMENT_COMPLETED)
                .and()

            // Payment failed → fail order + compensate inventory
            .withExternal()
                .source(Order.OrderStatus.PAYMENT_PROCESSING)
                .target(Order.OrderStatus.FAILED)
                .event(SagaEvent.PAYMENT_FAILED)
                .and()

            // Shipment created → order shipped
            .withExternal()
                .source(Order.OrderStatus.SHIPPING_PROCESSING)
                .target(Order.OrderStatus.SHIPPED)
                .event(SagaEvent.SHIPMENT_CREATED)
                .and()

            // Shipment failed → fail order + compensate inventory
            .withExternal()
                .source(Order.OrderStatus.SHIPPING_PROCESSING)
                .target(Order.OrderStatus.FAILED)
                .event(SagaEvent.SHIPMENT_FAILED)
                .and()

            // Delivery confirmed → complete
            .withExternal()
                .source(Order.OrderStatus.SHIPPED)
                .target(Order.OrderStatus.COMPLETED)
                .event(SagaEvent.DELIVERY_CONFIRMED);
    }
}
