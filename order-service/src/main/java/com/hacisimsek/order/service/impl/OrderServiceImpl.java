package com.hacisimsek.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.order.dto.OrderItemResponse;
import com.hacisimsek.order.dto.OrderRequest;
import com.hacisimsek.order.dto.OrderResponse;
import com.hacisimsek.order.model.Order;
import com.hacisimsek.order.model.OrderItem;
import com.hacisimsek.order.outbox.OutboxEvent;
import com.hacisimsek.order.outbox.OutboxEventRepository;
import com.hacisimsek.order.repository.OrderRepository;
import com.hacisimsek.order.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.hacisimsek.order.eventsourcing.OrderEvent;
import com.hacisimsek.order.eventsourcing.OrderEventService;
import com.hacisimsek.order.sse.OrderStatusEmitter;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OrderStatusEmitter orderStatusEmitter;
    private final OrderEventService orderEventService;
    private final Counter ordersCreatedCounter;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OutboxEventRepository outboxEventRepository,
                            ObjectMapper objectMapper,
                            OrderStatusEmitter orderStatusEmitter,
                            OrderEventService orderEventService,
                            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.orderStatusEmitter = orderStatusEmitter;
        this.orderEventService = orderEventService;
        this.ordersCreatedCounter = Counter.builder("zexxity.orders.created")
                .description("Total number of orders successfully created")
                .register(meterRegistry);
    }

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest orderRequest) {
        // ── 1. Build and save the Order ──────────────────────────────────────
        List<OrderItem> orderItems = orderRequest.getItems().stream()
                .map(item -> OrderItem.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        BigDecimal totalAmount = orderItems.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .customerId(orderRequest.getCustomerId())
                .customerEmail(orderRequest.getCustomerEmail())
                .totalAmount(totalAmount)
                .status(Order.OrderStatus.PENDING)
                .items(orderItems)
                .build();

        Order savedOrder = orderRepository.save(order);

        // ── 2. Build the Kafka event ──────────────────────────────────────────
        UUID correlationId = UUID.randomUUID();

        List<OrderItemDto> itemDtos = savedOrder.getItems().stream()
                .map(item -> new OrderItemDto(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice()))
                .collect(Collectors.toList());

        OrderCreatedEvent event = new OrderCreatedEvent(
                correlationId,
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getCustomerEmail(),
                itemDtos,
                savedOrder.getTotalAmount()
        );

        // ── 3. Write to the Outbox in the SAME transaction ───────────────────
        //
        // By writing the OutboxEvent inside the same @Transactional method,
        // both the Order row and the OutboxEvent row are committed atomically.
        // If Kafka is unavailable, the OutboxPublisher scheduler will pick up
        // and publish the pending row on the next tick (every 5 seconds).
        // This eliminates the "dual-write" race condition in the original code.
        try {
            OutboxEvent outboxEntry = OutboxEvent.builder()
                    .topic("order-events")
                    .aggregateId(savedOrder.getId())
                    .eventType(OrderCreatedEvent.class.getName())
                    .payload(objectMapper.writeValueAsString(event))
                    .status(OutboxEvent.Status.PENDING)
                    .build();

            outboxEventRepository.save(outboxEntry);
            log.info("Order {} saved with outbox entry (correlationId={})",
                    savedOrder.getId(), correlationId);
        } catch (JsonProcessingException ex) {
            // This would be a programming error (unparseable event) — rethrow
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent for outbox", ex);
        }

        // ── 4. Update status and metrics ─────────────────────────────────────
        ordersCreatedCounter.increment();
        savedOrder.setStatus(Order.OrderStatus.INVENTORY_CHECKING);
        orderRepository.save(savedOrder);

        // Append ORDER_CREATED event to the immutable event log
        orderEventService.append(
                savedOrder.getId(), correlationId,
                OrderEvent.EventType.ORDER_CREATED,
                null, Order.OrderStatus.PENDING,
                "order-service", "Order created with " + itemDtos.size() + " item(s)");

        // Append INVENTORY_CHECKING event
        orderEventService.append(
                savedOrder.getId(), correlationId,
                OrderEvent.EventType.INVENTORY_CHECKING,
                Order.OrderStatus.PENDING, Order.OrderStatus.INVENTORY_CHECKING,
                "order-service", "Saga started — checking inventory");

        // Push initial status to any SSE subscriber
        orderStatusEmitter.push(savedOrder.getId(), Order.OrderStatus.INVENTORY_CHECKING.name(), false);

        return mapToOrderResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    @Override
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderResponse> getOrdersByCustomerId(UUID customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateOrderStatus(UUID orderId, Order.OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        Order.OrderStatus previousStatus = order.getStatus();
        order.setStatus(status);
        orderRepository.save(order);
        log.info("Updated order {} status to {}", orderId, status);

        // Append transition event to the immutable event log
        OrderEvent.EventType eventType = resolveEventType(status);
        orderEventService.append(
                orderId, null,
                eventType,
                previousStatus, status,
                "saga", null);

        // Push real-time status update via SSE
        boolean terminal = isTerminalStatus(status);
        orderStatusEmitter.push(orderId, status.name(), terminal);
    }

    private OrderEvent.EventType resolveEventType(Order.OrderStatus status) {
        return switch (status) {
            case INVENTORY_CHECKING          -> OrderEvent.EventType.INVENTORY_CHECKING;
            case INVENTORY_RESERVED          -> OrderEvent.EventType.INVENTORY_RESERVED;
            case PAYMENT_PROCESSING          -> OrderEvent.EventType.PAYMENT_PROCESSING;
            case PAYMENT_COMPLETED           -> OrderEvent.EventType.PAYMENT_COMPLETED;
            case SHIPPING_PROCESSING         -> OrderEvent.EventType.SHIPPING_PROCESSING;
            case SHIPPED                     -> OrderEvent.EventType.ORDER_SHIPPED;
            case COMPLETED                   -> OrderEvent.EventType.ORDER_COMPLETED;
            case CANCELLED                   -> OrderEvent.EventType.ORDER_CANCELLED;
            case FAILED                      -> OrderEvent.EventType.ORDER_FAILED;
            default                          -> OrderEvent.EventType.ORDER_CREATED;
        };
    }

    /**
     * Terminal statuses — the saga has reached a final state.
     * After these, no further status changes will occur.
     */
    private boolean isTerminalStatus(Order.OrderStatus status) {
        return status == Order.OrderStatus.SHIPPED
                || status == Order.OrderStatus.COMPLETED
                || status == Order.OrderStatus.FAILED
                || status == Order.OrderStatus.CANCELLED;
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getPrice())
                        .build())
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .lastModifiedAt(order.getLastModifiedAt())
                .build();
    }
}
