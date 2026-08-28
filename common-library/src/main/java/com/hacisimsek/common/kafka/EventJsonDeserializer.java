package com.hacisimsek.common.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class EventJsonDeserializer implements Deserializer<Object> {

    private static final String TYPE_ID_HEADER = "__TypeId__";
    private static final String TRUSTED_EVENT_PACKAGE = "com.hacisimsek.common.event.";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // No external configuration required.
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        return deserialize(topic, null, data);
    }

    @Override
    public Object deserialize(String topic, Headers headers, byte[] data) {
        if (data == null) {
            return null;
        }

        Header typeHeader = headers == null ? null : headers.lastHeader(TYPE_ID_HEADER);
        if (typeHeader == null) {
            throw new IllegalArgumentException("Missing Kafka event type header " + TYPE_ID_HEADER
                    + " for topic " + topic);
        }

        String className = new String(typeHeader.value(), StandardCharsets.UTF_8);
        if (!className.startsWith(TRUSTED_EVENT_PACKAGE)) {
            throw new IllegalArgumentException("Untrusted Kafka event type: " + className);
        }

        try {
            Class<?> eventType = Class.forName(className);
            return objectMapper.readValue(data, eventType);
        } catch (ClassNotFoundException | IOException ex) {
            throw new IllegalArgumentException("Failed to deserialize Kafka event type: " + className, ex);
        }
    }
}
