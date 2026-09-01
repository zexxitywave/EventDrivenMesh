package com.hacisimsek.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Polls the outbox table every 5 seconds and publishes pending events to Kafka.
 *
 * Flow:
 *  1. Read all PENDING rows (oldest first)
 *  2. Deserialize payload back to the original event object
 *  3. Send to the target Kafka topic synchronously (get() with 10s timeout)
 *  4. On success → mark PUBLISHED
 *  5. On failure → increment retryCount; after 5 failures mark FAILED
 *
 * A nightly cleanup job removes PUBLISHED rows older than 7 days.
 *
 * Why synchronous send? Because we must know whether Kafka accepted the
 * message before marking it published. An async callback arriving after
 * a crash would leave the row in PENDING (safe — it will be retried).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)   // runs 5s after the previous run completes
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending =
                outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxEvent.Status.PENDING);

        if (pending.isEmpty()) return;

        log.debug("[Outbox] Processing {} pending event(s)", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Deserialize the stored JSON payload back to the original event class
                Class<?> eventClass = Class.forName(event.getEventType());
                Object eventPayload = objectMapper.readValue(event.getPayload(), eventClass);

                // Synchronous send — waits for broker ACK (or throws on timeout/error)
                SendResult<String, Object> result = kafkaTemplate
                        .send(event.getTopic(), event.getAggregateId().toString(), eventPayload)
                        .get();

                // Mark published
                event.setStatus(OutboxEvent.Status.PUBLISHED);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);

                log.info("[Outbox] Published {} → topic={} partition={} offset={}",
                        event.getEventType(),
                        event.getTopic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());

            } catch (Exception ex) {
                int retries = event.getRetryCount() + 1;
                event.setRetryCount(retries);
                event.setLastError(ex.getMessage() != null
                        ? ex.getMessage().substring(0, Math.min(ex.getMessage().length(), 1000))
                        : "unknown");

                if (retries >= MAX_RETRIES) {
                    event.setStatus(OutboxEvent.Status.FAILED);
                    log.error("[Outbox] Event {} FAILED after {} retries. Manual intervention required. Error: {}",
                            event.getId(), retries, ex.getMessage());
                } else {
                    log.warn("[Outbox] Publish attempt {}/{} failed for event {} ({}): {}",
                            retries, MAX_RETRIES, event.getId(), event.getEventType(), ex.getMessage());
                }
                outboxEventRepository.save(event);
            }
        }
    }

    /** Runs nightly to clean up old published events and keep the table small */
    @Scheduled(cron = "0 0 2 * * *")   // 02:00 every day
    @Transactional
    public void purgePublishedEvents() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        outboxEventRepository.deletePublishedBefore(cutoff);
        log.info("[Outbox] Purged published events older than 7 days");
    }
}
