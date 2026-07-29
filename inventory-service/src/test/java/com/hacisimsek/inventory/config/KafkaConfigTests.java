package com.hacisimsek.inventory.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.common.header.internals.RecordHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.hacisimsek.common.dto.OrderItemDto;
import com.hacisimsek.common.event.order.OrderCreatedEvent;

class KafkaConfigTests {

    @Test
    void consumerDeserializerUsesKafkaTypeHeader() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null, // customerEmail
                List.of(new OrderItemDto(UUID.randomUUID(), "Test Product", 1, BigDecimal.TEN)),
                BigDecimal.TEN
        );
        RecordHeaders headers = new RecordHeaders();
        byte[] payload = new JsonSerializer<>().serialize("order-events", headers, event);

        Object deserialized = new KafkaConfig().eventJsonDeserializer()
                .deserialize("order-events", headers, payload);

        assertThat(deserialized).isInstanceOf(OrderCreatedEvent.class);
    }
}
