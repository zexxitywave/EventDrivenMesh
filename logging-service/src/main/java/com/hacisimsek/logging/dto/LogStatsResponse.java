package com.hacisimsek.logging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogStatsResponse {
    private long totalLogs;
    private long errorCount;
    private long warnCount;
    private long infoCount;
    /** Error count per service name */
    private Map<String, Long> errorsByService;
    /** Log count per service name */
    private Map<String, Long> logsByService;
    /** Time window the stats cover (in minutes) */
    private int windowMinutes;
}
