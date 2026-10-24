package com.hacisimsek.logging.repository;

import com.hacisimsek.logging.model.LogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LogEntryRepository extends MongoRepository<LogEntry, UUID> {

    Page<LogEntry> findByServiceName(String serviceName, Pageable pageable);

    Page<LogEntry> findByLevel(LogEntry.LogLevel level, Pageable pageable);

    Page<LogEntry> findByServiceNameAndLevel(String serviceName, LogEntry.LogLevel level, Pageable pageable);

    Page<LogEntry> findByTimestampBetween(Instant from, Instant to, Pageable pageable);

    Page<LogEntry> findByServiceNameAndTimestampBetween(String serviceName, Instant from, Instant to, Pageable pageable);

    List<LogEntry> findByTraceId(String traceId);

    /** Count errors per service in a time window — used by alerting. */
    long countByServiceNameAndLevelAndTimestampAfter(String serviceName, LogEntry.LogLevel level, Instant after);

    long countByLevelAndTimestampAfter(LogEntry.LogLevel level, Instant after);

    /** Full-text search across message field. */
    @Query("{ 'message': { $regex: ?0, $options: 'i' } }")
    Page<LogEntry> searchByMessage(String keyword, Pageable pageable);

    /** Search with service filter. */
    @Query("{ 'serviceName': ?0, 'message': { $regex: ?1, $options: 'i' } }")
    Page<LogEntry> searchByServiceAndMessage(String serviceName, String keyword, Pageable pageable);

    List<LogEntry> findByLevelAndTimestampAfterOrderByTimestampDesc(LogEntry.LogLevel level, Instant after);
}
