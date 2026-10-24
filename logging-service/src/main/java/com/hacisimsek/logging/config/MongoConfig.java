package com.hacisimsek.logging.config;

import com.hacisimsek.logging.model.LogEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Sets up the MongoDB TTL index on LogEntry.expiresAt so old logs are
 * automatically purged without a scheduled cleanup job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoConfig {

    private final MongoTemplate mongoTemplate;

    @Value("${app.logging.retention-days:30}")
    private int retentionDays;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndexes() {
        // TTL index — MongoDB scans every 60 seconds and deletes expired documents
        mongoTemplate.indexOps(LogEntry.class)
                .ensureIndex(new Index("expiresAt", Sort.Direction.ASC)
                        .expire(Duration.ZERO));   // expire AT the value of the field
        log.info("MongoDB TTL index on log_entries.expiresAt ensured (retention={}d)", retentionDays);
    }
}
