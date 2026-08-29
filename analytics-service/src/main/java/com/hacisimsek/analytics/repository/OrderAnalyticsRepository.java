package com.hacisimsek.analytics.repository;

import com.hacisimsek.analytics.model.OrderAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OrderAnalyticsRepository extends JpaRepository<OrderAnalytics, Long> {

    // Total revenue across all orders
    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderAnalytics o")
    BigDecimal getTotalRevenue();

    // Total number of orders
    long count();

    // Top 10 customers by order count
    @Query("SELECT o.customerEmail, COUNT(o) as orderCount FROM OrderAnalytics o " +
           "GROUP BY o.customerEmail ORDER BY orderCount DESC")
    List<Object[]> getTopCustomers();

    // Revenue per day
    @Query(value = "SELECT DATE(event_timestamp) as day, SUM(total_amount) as revenue " +
                   "FROM order_analytics GROUP BY DATE(event_timestamp) ORDER BY day DESC",
           nativeQuery = true)
    List<Object[]> getRevenuePerDay();

    // Average order value
    @Query("SELECT AVG(o.totalAmount) FROM OrderAnalytics o")
    BigDecimal getAverageOrderValue();
}
