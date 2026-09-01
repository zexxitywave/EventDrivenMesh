package com.hacisimsek.logging.controller;

import com.hacisimsek.logging.model.AlertRule;
import com.hacisimsek.logging.service.AlertService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/logs/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /** List all alert rules. */
    @GetMapping
    public ResponseEntity<List<AlertRule>> getAll() {
        return ResponseEntity.ok(alertService.getAllRules());
    }

    /**
     * Create an alert rule.
     * Example: { "name":"High Payment Errors", "serviceName":"payment-service",
     *            "level":"ERROR", "threshold":5, "windowSeconds":60 }
     */
    @PostMapping
    public ResponseEntity<AlertRule> create(@Valid @RequestBody AlertRule rule) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createRule(rule));
    }

    /** Enable or disable a rule. */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AlertRule> toggle(
            @PathVariable UUID id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(alertService.toggleRule(id, enabled));
    }

    /** Delete a rule. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
        alertService.deleteRule(id);
        return ResponseEntity.ok(Map.of("message", "Alert rule deleted"));
    }
}
