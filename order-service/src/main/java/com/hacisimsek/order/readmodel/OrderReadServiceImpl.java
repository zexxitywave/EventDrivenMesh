package com.hacisimsek.order.readmodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.order.dto.OrderItemResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Serves GETs exclusively from the {@code order_reads} table — never touching
 * the write-side {@code orders} / {@code order_items} tables.
 */
@Service
@Slf4j
public class OrderReadServiceImpl implements OrderReadService {

    private final OrderReadModelRepository readModelRepository;
    private final ObjectMapper objectMapper;

    public OrderReadServiceImpl(OrderReadModelRepository readModelRepository, ObjectMapper objectMapper) {
        this.readModelRepository = readModelRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public OrderReadResponse getById(UUID orderId) {
        return readModelRepository.findById(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new RuntimeException("Order not found in read model with id: " + orderId));
    }

    @Override
    public List<OrderReadResponse> getAll() {
        return readModelRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderReadResponse> getByCustomerId(UUID customerId) {
        return readModelRepository.findByCustomerId(customerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private OrderReadResponse toResponse(OrderReadModel m) {
        List<OrderItemResponse> items = deserializeItems(m.getItemsJson());
        return OrderReadResponse.builder()
                .source(OrderReadResponse.SOURCE_READ_MODEL)
                .orderId(m.getOrderId())
                .customerId(m.getCustomerId())
                .customerEmail(m.getCustomerEmail())
                .totalAmount(m.getTotalAmount())
                .status(m.getStatus())
                .correlationId(m.getCorrelationId())
                .itemCount(m.getItemCount())
                .items(items)
                .refunded(m.isRefunded())
                .stockReleased(m.isStockReleased())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .lastEvent(m.getLastEvent())
                .lastEventAt(m.getLastEventAt())
                .build();
    }

    private List<OrderItemResponse> deserializeItems(String itemsJson) {
        if (itemsJson == null || itemsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<OrderItemDto> dtos = objectMapper.readValue(itemsJson, new TypeReference<List<OrderItemDto>>() {});
            return dtos.stream()
                    .map(dto -> OrderItemResponse.builder()
                            .productId(dto.getProductId())
                            .productName(dto.getProductName())
                            .quantity(dto.getQuantity())
                            .price(dto.getPrice())
                            .build())
                    .collect(Collectors.toList());
        } catch (JsonProcessingException e) {
            log.warn("Could not parse items_json: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}