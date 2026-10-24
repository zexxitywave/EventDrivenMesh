package com.hacisimsek.logging.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Configurable alert rule.
 * Example: "If ERROR count from payment-service > 5 in 60 seconds, fire alert."
 */
@Document(collection = "alert_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    private UUID id;

    private String name;
    private String serviceName;   // null = apply to all services
    private LogEntry.LogLevel level;
    private int threshold;        // number of occurrences
    private int windowSeconds;    // sliding window
    private boolean enabled;

    private Instant createdAt;
    private Instant lastTriggeredAt;
}
