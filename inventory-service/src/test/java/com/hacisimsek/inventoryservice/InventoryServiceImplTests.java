package com.hacisimsek.inventoryservice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.inventory.InventoryReservationFailedEvent;
import com.hacisimsek.common.event.inventory.InventoryReservedEvent;
import com.hacisimsek.common.event.order.OrderCreatedEvent;
import com.hacisimsek.inventory.model.InventoryItem;
import com.hacisimsek.inventory.model.InventoryStatus;
import com.hacisimsek.inventory.repository.InventoryRepository;
import com.hacisimsek.inventory.repository.ReservationRepository;
import com.hacisimsek.inventory.service.impl.InventoryServiceImpl;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTests {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void missingProductPublishesReservationFailedEvent() {
        UUID productId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(kafkaTemplate.send(eq("inventory-events"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        newService().reserveInventory(orderEvent(productId, 2));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("inventory-events"), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(InventoryReservationFailedEvent.class);
        assertThat(((InventoryReservationFailedEvent) eventCaptor.getValue()).getReason())
                .contains(productId.toString());
        verify(reservationRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    void successfulReservationReusesValidatedInventoryAndPublishesReservedEvent() {
        UUID productId = UUID.randomUUID();
        InventoryItem item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .availableQuantity(5)
                .reservedQuantity(0)
                .lowStockThreshold(1)
                .status(InventoryStatus.IN_STOCK)
                .build();
        when(reservationRepository.findByOrderId(any())).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductId(productId)).thenReturn(Optional.of(item));
        when(kafkaTemplate.send(eq("inventory-events"), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        newService().reserveInventory(orderEvent(productId, 2));

        verify(inventoryRepository).findByProductId(productId);
        verify(inventoryRepository).save(item);
        verify(reservationRepository, times(2)).save(any());
        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(item.getReservedQuantity()).isEqualTo(2);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("inventory-events"), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(InventoryReservedEvent.class);
    }

    private InventoryServiceImpl newService() {
        return new InventoryServiceImpl(inventoryRepository, reservationRepository, kafkaTemplate);
    }

    private OrderCreatedEvent orderEvent(UUID productId, int quantity) {
        return new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null, // customerEmail
                List.of(new OrderItemDto(productId, "Test Product", quantity, BigDecimal.TEN)),
                BigDecimal.TEN.multiply(BigDecimal.valueOf(quantity))
        );
    }
}
