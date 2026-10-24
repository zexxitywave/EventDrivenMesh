package com.hacisimsek.logging.service;

import com.hacisimsek.logging.model.AlertRule;
import com.hacisimsek.logging.model.LogEntry;
import com.hacisimsek.logging.repository.AlertRuleRepository;
import com.hacisimsek.logging.repository.LogEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRuleRepository alertRuleRepository;
    private final LogEntryRepository  logEntryRepository;

    /**
     * Runs every 60 seconds.
     * Evaluates every enabled alert rule against recent log counts.
     * In production, replace the log.warn() with an actual notification
     * (email, Slack webhook, PagerDuty, etc.).
     */
    @Scheduled(fixedDelay = 60_000)
    public void evaluateAlerts() {
        List<AlertRule> rules = alertRuleRepository.findByEnabled(true);
        if (rules.isEmpty()) return;

        for (AlertRule rule : rules) {
            Instant windowStart = Instant.now().minus(rule.getWindowSeconds(), ChronoUnit.SECONDS);

            long count = rule.getServiceName() != null
                    ? logEntryRepository.countByServiceNameAndLevelAndTimestampAfter(
                            rule.getServiceName(), rule.getLevel(), windowStart)
                    : logEntryRepository.countByLevelAndTimestampAfter(rule.getLevel(), windowStart);

            if (count >= rule.getThreshold()) {
                log.error("[ALERT TRIGGERED] Rule='{}' service='{}' level={} count={} threshold={} window={}s",
                        rule.getName(),
                        rule.getServiceName() != null ? rule.getServiceName() : "ALL",
                        rule.getLevel(),
                        count,
                        rule.getThreshold(),
                        rule.getWindowSeconds());

                // TODO: send notification via email/Slack/webhook
                // notificationClient.sendAlert(rule, count);

                rule.setLastTriggeredAt(Instant.now());
                alertRuleRepository.save(rule);
            }
        }
    }

    public AlertRule createRule(AlertRule rule) {
        rule.setId(UUID.randomUUID());
        rule.setCreatedAt(Instant.now());
        rule.setEnabled(true);
        return alertRuleRepository.save(rule);
    }

    public List<AlertRule> getAllRules() {
        return alertRuleRepository.findAll();
    }

    public AlertRule toggleRule(UUID id, boolean enabled) {
        AlertRule rule = alertRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert rule not found: " + id));
        rule.setEnabled(enabled);
        return alertRuleRepository.save(rule);
    }

    public void deleteRule(UUID id) {
        alertRuleRepository.deleteById(id);
    }
}
