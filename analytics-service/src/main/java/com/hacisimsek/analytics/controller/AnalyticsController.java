package com.hacisimsek.analytics.controller;

import com.hacisimsek.analytics.model.OrderAnalytics;
import com.hacisimsek.analytics.repository.OrderAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final OrderAnalyticsRepository repository;

    // GET /api/analytics/summary
    // Returns: total orders, total revenue, average order value
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalOrders", repository.count());
        summary.put("totalRevenue", repository.getTotalRevenue());
        summary.put("averageOrderValue", repository.getAverageOrderValue());
        return ResponseEntity.ok(summary);
    }

    // GET /api/analytics/orders
    // Returns: all order analytics records
    @GetMapping("/orders")
    public ResponseEntity<List<OrderAnalytics>> getAllOrders() {
        return ResponseEntity.ok(repository.findAll());
    }

    // GET /api/analytics/top-customers
    // Returns: customers ranked by order count
    @GetMapping("/top-customers")
    public ResponseEntity<List<Object[]>> getTopCustomers() {
        return ResponseEntity.ok(repository.getTopCustomers());
    }

    // GET /api/analytics/revenue-per-day
    // Returns: daily revenue breakdown
    @GetMapping("/revenue-per-day")
    public ResponseEntity<List<Object[]>> getRevenuePerDay() {
        return ResponseEntity.ok(repository.getRevenuePerDay());
    }
}
