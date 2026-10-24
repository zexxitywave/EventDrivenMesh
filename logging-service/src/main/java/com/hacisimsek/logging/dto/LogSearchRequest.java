package com.hacisimsek.logging.dto;

import com.hacisimsek.logging.model.LogEntry;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

@Data
public class LogSearchRequest {
    private String serviceName;
    private LogEntry.LogLevel level;
    private String keyword;
    private String traceId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    private int page = 0;
    private int size = 50;
}
