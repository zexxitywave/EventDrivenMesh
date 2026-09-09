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
