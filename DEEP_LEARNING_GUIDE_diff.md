--- DEEP_LEARNING_GUIDE.md (原始)


+++ DEEP_LEARNING_GUIDE.md (修改后)
# 🎓 Deep Learning Guide: EventDrivenMesh

This guide provides step-by-step exercises to master the advanced distributed systems patterns in this project.

---

## 📋 Prerequisites

Before starting these exercises, ensure you have:

```bash
# 1. All services running
docker-compose up -d

# 2. Kafka topics created
docker-compose exec broker kafka-topics --bootstrap-server localhost:9092 --list

# 3. Test data available
curl http://localhost:8080/api/v1/orders/test-data
```

Expected topics:
- `order-events`
- `inventory-events`
- `payment-events`
- `shipping-events`
- `order-analytics-dlq`

---

## 🔍 Exercise 1: Trace a Full Order Using correlationId

**Goal:** Understand how events flow across services in a Saga.

### Step 1.1: Create a Test Order

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -H "X-User-Email: test@example.com" \
  -d '{
    "customerId": "cust-001",
    "items": [
      {"productId": "prod-001", "quantity": 2, "price": 29.99},
      {"productId": "prod-002", "quantity": 1, "price": 49.99}
    ],
    "shippingAddress": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zipCode": "10001",
      "country": "USA"
    }
  }'
```

**Save the `orderId` from the response.**

### Step 1.2: Extract the correlationId

Check the order service logs:

```bash
docker-compose logs order-service | grep "OrderCreatedEvent" | tail -5
```

Look for JSON like:
```json
{
  "eventId": "a1b2c3d4-...",
  "correlationId": "SAME-UUID-HERE",
  "eventType": "OrderCreatedEvent",
  "orderId": "ORD-123456"
}
```

**Copy the `correlationId` value.**

### Step 1.3: Trace Across All Services

Search for this correlationId in all service logs:

```bash
# Order Service
docker-compose logs order-service | grep "SAME-UUID-HERE"

# Inventory Service
docker-compose logs inventory-service | grep "SAME-UUID-HERE"

# Payment Service
docker-compose logs payment-service | grep "SAME-UUID-HERE"

# Shipping Service
docker-compose logs shipping-service | grep "SAME-UUID-HERE"
```

### Step 1.4: Visualize the Flow

Create a timeline:

```
[T0] Order Service     → OrderCreatedEvent (correlationId: abc123)
[T1] Inventory Service → InventoryReservedEvent (correlationId: abc123)
[T2] Payment Service   → PaymentProcessedEvent (correlationId: abc123)
[T3] Shipping Service  → ShipmentProcessedEvent (correlationId: abc123)
```

**Learning Outcome:** You'll see how a single business transaction spans multiple services, all linked by `correlationId`.

### Bonus: Use Kafka CLI to Inspect Events

```bash
# Read order-events topic
docker-compose exec broker kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-events \
  --from-beginning \
  --max-messages 10

# Filter by correlationId (grep the output)
```

---

## ☠️ Exercise 2: Simulate Failures (Kill Inventory Service Mid-Saga)

**Goal:** Observe Saga compensation when a participant fails.

### Step 2.1: Start Fresh

```bash
# Clear existing test data
docker-compose exec postgres psql -U admin -d order_db -c "TRUNCATE orders CASCADE;"
docker-compose exec mongo mongosh inventory_db -eval "db.orders.deleteMany({})"
```

### Step 2.2: Stop Inventory Service

```bash
docker-compose stop inventory-service
```

Verify it's down:
```bash
docker-compose ps inventory-service
# Should show "Exited"
```

### Step 2.3: Create an Order

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Id: user-123" \
  -H "X-User-Email: test@example.com" \
  -d '{
    "customerId": "cust-001",
    "items": [{"productId": "prod-001", "quantity": 5, "price": 29.99}],
    "shippingAddress": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zipCode": "10001",
      "country": "USA"
    }
  }'
```

### Step 2.4: Observe the Failure

Watch order-service logs:
```bash
docker-compose logs -f order-service
```

You should see:
- `OrderCreatedEvent` published ✅
- Waiting for `InventoryReservedEvent`... ⏳
- **Timeout or no response** ❌

### Step 2.5: Check Order Status

```bash
curl http://localhost:8081/api/v1/orders/{orderId}
```

Expected status: `PENDING` (stuck waiting for inventory)

### Step 2.6: Restart Inventory Service

```bash
docker-compose start inventory-service
docker-compose logs -f inventory-service
```

**Question:** Does the saga automatically resume?

**Answer:** No! This is a limitation of choreography-based sagas. The inventory service missed the event while down.

### Step 2.7: Implement Compensation (Manual Fix)

To fix this in production, you'd need:

1. **Event Replay:** Re-publish the `OrderCreatedEvent`
2. **Timeout + Compensation:** Order service detects timeout and publishes `CancelOrderCommand`

**Advanced Challenge:** Modify `OrderSagaHandler.java` to add a timeout that triggers cancellation after 30 seconds.

---

## 🔄 Exercise 3: Replay DLQ Messages After Fixing Schema

**Goal:** Learn how to recover from poison pill scenarios.

### Step 3.1: Send a Malformed Event

```bash
# Publish a message missing required fields (poison pill)
docker-compose exec broker kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order-analytics

> {"eventId": "bad-001", "eventType": "OrderCompletedEvent", "correlationId": "test-123"}
> (missing orderId, customerId, totalAmount - required fields!)
> Ctrl+C
```

### Step 3.2: Watch Analytics Service Logs

```bash
docker-compose logs -f analytics-service | grep -i "poison\|dlq\|error"
```

Expected output:
```
WARN Poison pill detected — missing orderId. Routing to DLQ.
```

### Step 3.3: Verify DLQ Message

```bash
# Consume from DLQ topic
docker-compose exec broker kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-analytics-dlq \
  --from-beginning \
  --max-messages 5 \
  --property print.key=true
```

You should see your malformed message with metadata about why it failed.

### Step 3.4: Fix the Schema Issue

In a real scenario, you'd:
1. Identify the root cause (e.g., producer bug)
2. Fix the producer code
3. Deploy the fix

For this exercise, manually create a corrected message.

### Step 3.5: Replay the Fixed Message

```bash
# Option A: Manually re-publish corrected message
docker-compose exec broker kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic order-analytics

> {"eventId": "bad-001-fixed", "eventType": "OrderCompletedEvent", "correlationId": "test-123", "orderId": "ORD-999", "customerId": "cust-001", "totalAmount": 99.99, ...}

# Option B: Write a replay script (advanced)
```

### Step 3.6: Verify Analytics Data

```bash
curl http://localhost:8084/api/analytics/orders/ORD-999
```

**Learning Outcome:** DLQ allows you to isolate bad messages without blocking the entire consumer, enabling manual or automated recovery.

---

## 📈 Exercise 4: Scale Partitions and Measure Throughput

**Goal:** Understand Kafka partitioning and parallelism.

### Step 4.1: Check Current Partition Count

```bash
docker-compose exec broker kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic order-events
```

Expected: `PartitionCount: 3`

### Step 4.2: Baseline Throughput Test

Run the JMeter load test:

```bash
cd /workspace/load-tests
jmeter -n -t order-flow.jmx -l results-baseline.jtl
```

Or use the provided script:
```bash
./run-load-test.sh --duration 60 --threads 10
```

Record:
- Requests per second: ___
- Average latency: ___ ms
- Error rate: ___%

### Step 4.3: Increase Partitions to 6

```bash
docker-compose exec broker kafka-topics \
  --bootstrap-server localhost:9092 \
  --alter \
  --topic order-events \
  --partitions 6
```

Verify:
```bash
docker-compose exec broker kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic order-events
```

### Step 4.4: Scale Consumers

Update `docker-compose.yml`:

```yaml
inventory-service:
  deploy:
    replicas: 3  # Was 1
```

Restart:
```bash
docker-compose up -d --scale inventory-service=3
```

### Step 4.5: Run Load Test Again

```bash
cd /workspace/load-tests
jmeter -n -t order-flow.jmx -l results-scaled.jtl
```

Record:
- Requests per second: ___
- Average latency: ___ ms
- Error rate: ___%

### Step 4.6: Compare Results

| Metric | Baseline (3 partitions) | Scaled (6 partitions) | Improvement |
|--------|-------------------------|-----------------------|-------------|
| Throughput | ___ req/s | ___ req/s | +___% |
| Latency (p95) | ___ ms | ___ ms | -___% |
| Errors | ___% | ___% | ___ |

**Key Insight:** More partitions = more parallel consumers = higher throughput (up to a point).

---

## ➕ Exercise 5: Add a New Saga Participant (Loyalty Service)

**Goal:** Extend the saga with a new service.

### Step 5.1: Create Loyalty Service Structure

```bash
mkdir -p /workspace/loyalty-service/src/main/java/com/eventdrivenmesh/loyalty/{service,event,listener}
mkdir -p /workspace/loyalty-service/src/main/resources
```

### Step 5.2: Define Loyalty Event

Create `/workspace/loyalty-service/src/main/java/com/eventdrivenmesh/loyalty/event/LoyaltyPointsAddedEvent.java`:

```java
package com.eventdrivenmesh.loyalty.event;

import com.eventdrivenmesh.common.base.BaseEvent;
import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoyaltyPointsAddedEvent extends BaseEvent {
    private String customerId;
    private Integer pointsEarned;
    private BigDecimal orderTotal;
    private String loyaltyTier;
}
```

### Step 5.3: Create Event Listener

Create `/workspace/loyalty-service/src/main/java/com/eventdrivenmesh/loyalty/listener/PaymentCompletedListener.java`:

```java
package com.eventdrivenmesh.loyalty.listener;

import com.eventdrivenmesh.common.payment.PaymentProcessedEvent;
import com.eventdrivenmesh.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedListener {

    private final LoyaltyService loyaltyService;

    @KafkaListener(
        topics = "payment-events",
        groupId = "loyalty-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenPaymentCompleted(ConsumerRecord<String, PaymentProcessedEvent> record) {
        PaymentProcessedEvent event = record.value();

        log.info("Received PaymentProcessedEvent for order {} - calculating loyalty points",
                 event.getOrderId());

        // Only process successful payments
        if ("COMPLETED".equals(event.getStatus())) {
            loyaltyService.calculateAndAwardPoints(event);
        }
    }
}
```

### Step 5.4: Implement Loyalty Service

Create `/workspace/loyalty-service/src/main/java/com/eventdrivenmesh/loyalty/service/LoyaltyService.java`:

```java
package com.eventdrivenmesh.loyalty.service;

import com.eventdrivenmesh.common.payment.PaymentProcessedEvent;
import com.eventdrivenmesh.loyalty.event.LoyaltyPointsAddedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoyaltyService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void calculateAndAwardPoints(PaymentProcessedEvent paymentEvent) {
        // Business logic: 1 point per $1 spent, bonus for premium tier
        BigDecimal orderTotal = paymentEvent.getAmount();
        int basePoints = orderTotal.intValue();
        int bonusPoints = isPremiumCustomer(paymentEvent.getCustomerId()) ? basePoints / 2 : 0;
        int totalPoints = basePoints + bonusPoints;

        String tier = determineLoyaltyTier(totalPoints);

        LoyaltyPointsAddedEvent loyaltyEvent = LoyaltyPointsAddedEvent.builder()
            .eventId(UUID.randomUUID())
            .correlationId(paymentEvent.getCorrelationId())
            .customerId(paymentEvent.getCustomerId())
            .pointsEarned(totalPoints)
            .orderTotal(orderTotal)
            .loyaltyTier(tier)
            .build();

        log.info("Awarding {} points to customer {} (tier: {})",
                 totalPoints, paymentEvent.getCustomerId(), tier);

        kafkaTemplate.send("loyalty-events", loyaltyEvent);
    }

    private boolean isPremiumCustomer(String customerId) {
        // TODO: Query customer database
        return false; // Simplified for demo
    }

    private String determineLoyaltyTier(int totalPoints) {
        if (totalPoints > 1000) return "PLATINUM";
        if (totalPoints > 500) return "GOLD";
        if (totalPoints > 100) return "SILVER";
        return "BRONZE";
    }
}
```

### Step 5.5: Update application.yml

Add to `/workspace/loyalty-service/src/main/resources/application.yml`:

```yaml
spring:
  kafka:
    consumer:
      group-id: loyalty-service-group
      auto-offset-reset: earliest
    topics:
      - payment-events
      - loyalty-events
```

### Step 5.6: Register New Topic

```bash
docker-compose exec broker kafka-topics \
  --bootstrap-server localhost:9092 \
  --create \
  --topic loyalty-events \
  --partitions 3 \
  --replication-factor 1
```

### Step 5.7: Build and Run Loyalty Service

```bash
cd /workspace/loyalty-service
../mvnw clean install -DskipTests
docker-compose up -d loyalty-service
```

### Step 5.8: Test the Extended Saga

1. Create an order (triggers full saga)
2. Wait for payment completion
3. Check loyalty-events topic:

```bash
docker-compose exec broker kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic loyalty-events \
  --from-beginning \
  --max-messages 5
```

You should see `LoyaltyPointsAddedEvent` with the same `correlationId` as the original order!

### Step 5.9: Visualize Updated Saga Flow

```
Order Created → Inventory Reserved → Payment Processed → Loyalty Points Added → Shipment Created
       ↓                ↓                   ↓                    ↓                     ↓
   [order-events]  [inventory-events]  [payment-events]    [loyalty-events]     [shipping-events]
```

**Learning Outcome:** You've successfully extended the saga with a new participant without modifying existing services!

---

## 📊 Tracking Your Progress

Use this checklist:

- [ ] **Exercise 1:** Traced correlationId across all 4 services
- [ ] **Exercise 2:** Simulated inventory failure and observed saga behavior
- [ ] **Exercise 3:** Sent poison pill, verified DLQ, replayed fixed message
- [ ] **Exercise 4:** Scaled partitions from 3→6 and measured throughput improvement
- [ ] **Exercise 5:** Added Loyalty Service as a new saga participant

---

## 🎯 Advanced Challenges

Once you complete all exercises:

1. **Add Timeout Compensation:** Modify Order Service to cancel orders if inventory doesn't respond within 30s
2. **Implement Idempotency in Loyalty Service:** Prevent duplicate point awards on replay
3. **Add Grafana Dashboard:** Visualize saga success/failure rates
4. **Chaos Engineering:** Use Chaos Mesh to randomly kill services during saga execution
5. **Multi-Region Deployment:** Deploy services across multiple Kubernetes clusters

---

## 📚 Additional Resources

- **Kafka Documentation:** https://kafka.apache.org/documentation/
- **Saga Pattern (Microservices.io):** https://microservices.io/patterns/data/saga.html
- **Designing Data-Intensive Applications (Book):** Chapter 11 on Stream Processing
- **CNCF Distributed Transactions Whitepaper:** https://www.cncf.io/

---

**Happy Learning! 🚀**

Remember: The best way to master distributed systems is to break them, observe failures, and implement fixes. Don't be afraid to experiment!
