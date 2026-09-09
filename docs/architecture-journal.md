# Event-Driven Mesh - Architecture Journal

Incremental design notes recorded as the mesh evolved, one commit per entry.


## 1. Scaffold the workspace

Initialize the monorepo layout with one module per service, a shared common-library for event contracts and cross-service DTOs, and docker-compose for all local infrastructure.

---


## 2. Shared event contracts

Introduce common-library holding BaseEvent (eventId, correlationId, timestamp), typed Kafka publishers and consumers, plus the error envelope shared by every REST controller.

---


## 3. Service discovery

Register every service with spring-cloud-netflix-eureka so the gateway resolves lb:// URIs and instances auto-register and heartbeat on boot.

---


## 4. Edge gateway

Add api-gateway with JWT validation at the edge, per-user rate limiting, and routes to /api/v1/** across the mesh.

---


## 5. order-service skeleton

Create order-service with an OrderCreatedEvent producer on the order-events topic and a REST controller for the order lifecycle.

---


## 6. Checkout triggers the saga

On checkout the order-service publishes OrderCreatedEvent so the saga chain begins: order, inventory, payment, shipping, notification.

---


## 7. Saga correlation

Add correlationId propagation on every event envelope so retries, logs and metrics trace an order through each saga hop.

---


## 8. Inventory reservation

inventory-service consumes order-events, reserves stock, then publishes InventoryReservedEvent, or InventoryReservationFailedEvent on shortage.

---


## 9. Inventory compensation

When a later saga step fails, inventory-service consumes compensating events and frees the reserved stock.

---


## 10. Payment processing

payment-service consumes inventory-events, processes the charge, then publishes PaymentSucceededEvent or PaymentFailedEvent.

---


## 11. Payment compensation

PaymentFailedEvent rolls back the inventory reservation and triggers the buyer notification path.

---


## 12. Shipping fulfillment

shipping-service consumes payment-events, creates the shipment, and publishes ShippingCompletedEvent to close the main saga chain.

---


## 13. Shipping failure path

ShippingFailedEvent carries the cause so analytics and notification consumers can react deterministically.

---


## 14. Notification fan-out

notification-service consumes ShippingCompletedEvent, builds an email with the order summary and persists a Notification in Mongo.

---


## 15. Invoice PDF generation

Notification emails carry an invoice PDF generated from order data and attached through JavaMailSender.

---


## 16. Notification model

Mongo notifications track PENDING, RETRY_PENDING, SENT and FAILED transitions with a retry counter on each document.

---


## 17. Retry background job

A scheduled job revisits RETRY_PENDING notifications so transient SMTP failures are retried instead of dropped.

---


## 18. Analytics read model

analytics-service consumes order, inventory, payment and shipping events into denormalized projections for dashboards.

---


## 19. Idempotent projection

Projections are keyed by eventId so at-least-once Kafka redelivery never double-counts a saga step.

---


## 20. First DLQ: order-analytics-dlq

Records that fail to parse or project are retried briefly, then published to order-analytics-dlq for inspection and replay.

---


## 21. Audit aggregation

logging-service consumes every event topic and writes a time-bucketed audit trail for support and forensics.

---
