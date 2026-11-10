package com.flashsale.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {

    private final Counter reservationSuccess;
    private final Counter reservationFailure;
    private final Counter kafkaProcessed;
    private final Counter kafkaDuplicate;

    public MetricsService(MeterRegistry meterRegistry) {

        reservationSuccess = Counter.builder(
                        "flashsale.reservation.success"
                )
                .description("Successful inventory reservations")
                .register(meterRegistry);

        reservationFailure = Counter.builder(
                        "flashsale.reservation.failure"
                )
                .description("Failed inventory reservations")
                .register(meterRegistry);

        kafkaProcessed = Counter.builder(
                        "flashsale.kafka.processed"
                )
                .description("Kafka inventory events processed")
                .register(meterRegistry);

        kafkaDuplicate = Counter.builder(
                        "flashsale.kafka.duplicate"
                )
                .description("Duplicate Kafka events skipped")
                .register(meterRegistry);
    }

    public void reservationSuccess() {
        reservationSuccess.increment();
    }

    public void reservationFailure() {
        reservationFailure.increment();
    }

    public void kafkaProcessed() {
        kafkaProcessed.increment();
    }

    public void kafkaDuplicate() {
        kafkaDuplicate.increment();
    }
}
