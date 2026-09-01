package com.hacisimsek.order.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active SSE (Server-Sent Events) connections for order status updates.
 *
 * Each client subscribes to a specific orderId. When the order status changes
 * (driven by saga events), the saga handler calls {@link #push} and the update
 * is streamed instantly to any connected browser/client — no polling needed.
 *
 * Connection lifecycle:
 *   - Client opens  GET /api/orders/{orderId}/status-stream
 *   - Server holds the connection open (SseEmitter with 5-min timeout)
 *   - On status change → push event to that orderId's emitter
 *   - On COMPLETED/CANCELLED/FAILED → push final event and complete the stream
 *   - On timeout or client disconnect → emitter is cleaned up automatically
 *
 * Thread safety: ConcurrentHashMap handles concurrent subscribe/push/cleanup.
 */
@Component
@Slf4j
public class OrderStatusEmitter {

    // orderId → active SseEmitter for that order
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** SSE connection timeout — 5 minutes. Client should reconnect if needed. */
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * Register a new SSE connection for the given orderId.
     * Returns the emitter to be written directly to the HTTP response.
     */
    public SseEmitter subscribe(UUID orderId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        // Clean up on completion, timeout, or error
        emitter.onCompletion(() -> {
            emitters.remove(orderId);
            log.debug("[SSE] Connection completed for order {}", orderId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(orderId);
            log.debug("[SSE] Connection timed out for order {}", orderId);
        });
        emitter.onError(ex -> {
            emitters.remove(orderId);
            log.debug("[SSE] Connection error for order {}: {}", orderId, ex.getMessage());
        });

        emitters.put(orderId, emitter);
        log.info("[SSE] Client subscribed to order {} status stream (active connections: {})",
                orderId, emitters.size());

        // Send an initial "connected" event so the client knows the stream is live
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"orderId\":\"" + orderId + "\",\"message\":\"Subscribed to order status stream\"}"));
        } catch (IOException e) {
            emitters.remove(orderId);
        }

        return emitter;
    }

    /**
     * Push a status update to the client subscribed to this orderId.
     * Called by {@link com.hacisimsek.order.service.impl.OrderServiceImpl}
     * whenever the order status changes.
     *
     * @param orderId   the order that changed
     * @param newStatus the new status string (e.g. "PAYMENT_COMPLETED")
     * @param terminal  true if this is the final state (SHIPPED, COMPLETED, FAILED, CANCELLED)
     */
    public void push(UUID orderId, String newStatus, boolean terminal) {
        SseEmitter emitter = emitters.get(orderId);
        if (emitter == null) {
            // No connected client — that's fine, most users poll instead of streaming
            return;
        }

        String payload = String.format(
                "{\"orderId\":\"%s\",\"status\":\"%s\",\"timestamp\":\"%s\"}",
                orderId, newStatus, Instant.now());

        try {
            emitter.send(SseEmitter.event()
                    .name("status-update")
                    .data(payload));

            log.info("[SSE] Pushed status {} to order {} subscriber", newStatus, orderId);

            // Complete the stream on terminal states — no more updates coming
            if (terminal) {
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("{\"message\":\"Order reached terminal state: " + newStatus + "\"}"));
                emitter.complete();
                emitters.remove(orderId);
            }
        } catch (IOException e) {
            log.warn("[SSE] Failed to push to order {} subscriber — removing: {}", orderId, e.getMessage());
            emitters.remove(orderId);
        }
    }

    /** Returns the number of active SSE connections — useful for monitoring */
    public int activeConnections() {
        return emitters.size();
    }
}
