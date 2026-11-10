# EventDrivenMesh

*"Polyglot Persistence · Choreography-Based Saga · Zero-Downtime Kubernetes Deployments"*

[![CI](https://github.com/zexxitywave/EventDrivenMesh/actions/workflows/ci.yml/badge.svg)](https://github.com/zexxitywave/EventDrivenMesh/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F)](https://spring.io/projects/spring-boot)
[![Prometheus](https://img.shields.io/badge/Prometheus-E6522C)](https://prometheus.io/)
[![Grafana](https://img.shields.io/badge/Grafana-F46800)](https://grafana.com/)

---

## 📖 About

**EventDrivenMesh** is a production-grade, event-driven e-commerce backend built as a mesh of 18 application services — 17 Spring Boot services and one Go service — registered with Eureka behind a central API Gateway. Services communicate asynchronously over Apache Kafka using a choreography-based saga with automatic compensation, and synchronously over REST through the gateway, which validates JWTs once and injects trusted identity headers downstream.

The platform covers the full commerce lifecycle: catalog and semantic search, cart and wishlist, seller onboarding, checkout, inventory reservation, payments (Razorpay / Stripe / Mock), pre-payment delivery quoting, shipping, PDF invoices and transactional email, coupons, flash sales, location lookup, CQRS analytics, and centralized logging. Each service owns its data store — PostgreSQL 16 with pgvector, MongoDB, or Redis — while the Go notification service adds RabbitMQ priority queues for high-throughput delivery, and n8n drives abandoned-cart recovery outside the Java services.

Everything ships production-style: Docker Compose for local infrastructure, Kubernetes manifests with HPA and zero-downtime rollouts, versioned images on GHCR, GitHub Actions CI, and a Prometheus / Grafana / OpenTelemetry observability stack. The checkout saga has been load tested end-to-end with 30,000 orders at a 0% error rate.

---

### Language

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Go](https://img.shields.io/badge/Go-00ADD8?style=for-the-badge&logo=go&logoColor=white)

### Frameworks & Cloud

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.2-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring%20Security-JWT%20%2B%20OAuth2-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white)

### Infrastructure

![Kafka](https://img.shields.io/badge/Apache%20Kafka-KRaft-231F20?style=for-the-badge&logo=apachekafka&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![pgvector](https://img.shields.io/badge/pgvector-768--dim-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-7.0-47A248?style=for-the-badge&logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=for-the-badge&logo=redis&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-HPA-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white)

### Observability & Tools

![Prometheus](https://img.shields.io/badge/Prometheus-Metrics-E6522C?style=for-the-badge&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-Dashboards-F46800?style=for-the-badge&logo=grafana&logoColor=white)
![Kafka UI](https://img.shields.io/badge/Kafka%20UI-Kafbat-orange?style=for-the-badge)
![n8n](https://img.shields.io/badge/n8n-Workflow-EA4B71?style=for-the-badge&logo=n8n&logoColor=white)
![OpenTelemetry](https://img.shields.io/badge/OpenTelemetry-425CC7?style=for-the-badge&logo=opentelemetry&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub%20Actions-CI-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Multi--Module-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white)

---

## 📋 Table of Contents

- [About](#-about)
- [Key Highlights](#-key-highlights)
- [Architecture](architecture.md)
- [Quick Start](#-quick-start)
- [Service Port Reference](#-service-port-reference)
- [Core Services](#-core-services)
- [Additional Services](#-additional-services)
- [n8n Automation](#-n8n-automation-abandoned-cart-recovery)
- [Kafka Topics](#-kafka-topics)
- [Kubernetes](#-kubernetes)
- [Load Testing Results](#-load-testing-results)
- [Observability](#-observability)
- [Releases](#-releases)
- [Tech Stack](#-tech-stack)

---

## ✨ Key Highlights

- **Event-Driven Saga** — Checkout flows orchestrated through Kafka events with automatic compensating rollbacks across inventory, payment, and shipping. No single point of failure.
- **Pre-Payment Delivery Quotes** — order-service prices delivery at checkout by calling shipping-service (`POST /api/v1/shipping/quotes`). Free-text addresses geocoded via OpenStreetMap Nominatim, bucketed into Uber H3 hexagons, nearest hub resolved by Haversine distance. `totalAmount` = items + delivery in one payment.
- **Polyglot Persistence** — PostgreSQL for transactions, MongoDB for documents, Redis for session cache. Each service owns its own data store.
- **Gateway-Level Security** — JWT validated once at the API Gateway. Trusted identity headers (`X-User-Id`, `X-User-Email`, `X-User-Role`) injected downstream — no repeated token parsing.
- **PDF Invoice Generation** — Auto-generated on every order via OpenPDF, attached to confirmation email, stored in MongoDB, downloadable anytime.
- **Semantic Product Search** — pgvector with HNSW cosine-distance index and 768-dim Ollama `nomic-embed-text` embeddings. `EmbeddingInitializer` backfills at startup.
- **CQRS Analytics** — Dedicated PostgreSQL analytics DB, batch Kafka consumer at ~500 events/sec, poison-pill DLQ, idempotency via `event_id` unique constraint.
- **n8n Abandoned-Cart Recovery** — 23-node n8n workflow polls idle carts, re-checks orders, sends reminder email, waits 24h, sends coupon. Workflow state in Redis — no duplicate sends.
- **Kubernetes-Ready** — HPA-configured (2–8 pods), zero-downtime rolling updates, liveness/readiness probes, pod anti-affinity, Prometheus scraping.
- **Scalable Notification Service (Go)** — High-throughput notification system built in Go with RabbitMQ priority queues, PostgreSQL, OpenTelemetry tracing, and Prometheus metrics.
- **Location Service** — Browser geolocation → reverse geocoding via OSM Nominatim → PostgreSQL persistence.
- **Sale Service** — Flash sale and promotion management with Kafka event publishing.
- **Full Observability** — Micrometer → Prometheus → Grafana. HTTP latency p50/p95/p99, Kafka consumer lag, JVM heap, HikariCP pool.
- **GitHub Actions CI** — Builds all modules on every push.

---

## 🏗️ Architecture

EventDrivenMesh is an event-driven microservices platform: backend services communicate via Apache Kafka (choreographed saga) while the API Gateway routes client traffic over REST.

The full architecture — system topology, HLD, checkout saga, and microservice catalog — lives in [**architecture.md**](architecture.md).

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Docker Desktop | Latest |
| Maven | 3.9+ |
| Ollama | Latest (for semantic search) |

### 1. Configure Environment

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=postgres123

# JWT
JWT_SECRET=your_jwt_secret_here

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Razorpay
RAZORPAY_KEY_ID=your_razorpay_key_id
RAZORPAY_KEY_SECRET=your_razorpay_key_secret

# Email (Resend)
MAIL_PASSWORD=your_resend_api_key
```

### 2. Start Infrastructure

```bash
docker-compose up -d
```

| Container | Port |
|---|---|
| Kafka (KRaft) | `9095` |
| PostgreSQL 16 | `5432` |
| MongoDB | `27017` |
| Redis 7 | `6379` |
| Kafka UI | `8069` |
| Prometheus | `9091` |
| Grafana | `3000` |

### 3. Build & Start

```bash
./mvnw clean install -DskipTests
```

Start in order: `service-registry` → `api-gateway` → `auth-service` → all others.

> **Semantic search:** `ollama pull nomic-embed-text && ollama serve` before starting product-service.

---

## 💡 Service Port Reference

| Service | Port | Database |
|---|---|---|
| service-registry | `8761` | — |
| api-gateway | `8080` | — |
| auth-service | `8086` | PostgreSQL `auth_db` |
| user-service | `8087` | PostgreSQL `user_db` |
| product-service | `8088` | PostgreSQL `product_db` |
| seller-service | `8091` | PostgreSQL `seller_db` |
| cart-service | `8089` | Redis |
| wishlist-service | `8090` | MongoDB `wishlist_db` |
| order-service | `8081` | PostgreSQL `order_db` |
| inventory-service | `8082` | MongoDB `inventory` |
| payment-service | `8083` | PostgreSQL `payment_db` |
| shipping-service | `8085` | PostgreSQL `shipping_db` |
| notification-service | `8084` | MongoDB `notification_db` |
| coupon-service | `8097` | PostgreSQL `coupon_db` |
| logging-service | `8092` | MongoDB `logging_db` |
| analytics-service | `8093` | PostgreSQL `analytics_db` |
| sale-service | `8094` | PostgreSQL `sale_db` |
| location-service | `8095` | PostgreSQL |
| scalable-notification-service | `8096` | PostgreSQL |

---

## 🧩 Core Services

### API Gateway · `8080`
Single entry point. Validates JWT, injects `X-User-Id` / `X-User-Email` / `X-User-Role` headers downstream.

### Auth Service · `8086`
Registration, email OTP verification, login, JWT issuance, refresh token rotation, password reset, Google OAuth2.
- Access token: 15 min · Refresh token: 7 days (rotated on use) · OTP: 15 min via AWS SES

### User Service · `8087`
User profiles, shipping addresses, preferences. Internal contact lookup endpoint for n8n.

### Product Service · `8088`
Full catalog with categories, filters, pagination. Semantic search via pgvector HNSW + Ollama embeddings.

### Cart Service · `8089`
Redis-backed cart (TTL 7 days). Stock validation against inventory-service. Abandoned cart support with `ReminderStage` tracking (`NONE → REMINDED → COUPON_SENT`). Coupons are validated through coupon-service, lower the cart total, and are redeemed at checkout.

**n8n internal endpoints:**
- `GET /api/v1/cart/internal/abandoned` — scan idle carts by stage
- `PATCH /api/v1/cart/internal/{userId}/reminder-state` — update reminder stage
- `POST /api/v1/cart/internal/{userId}/simulate-idle` — backdate for testing

### Coupon Service · `8097`
Generates short-lived discount codes (`CART10-XXXXXXXX`) and validates/redeems them. Used by cart-service at apply/checkout time and by the n8n workflow to put a real, valid code in the second recovery email.

**API:** `POST /api/v1/coupons/generate`, `GET /api/v1/coupons/validate`, `POST /api/v1/coupons/redeem`, `GET /api/v1/coupons/exists`

### Order Service · `8081`
Drives the entire checkout saga. Quotes delivery from shipping-service, publishes `OrderCreatedEvent`, reacts to inventory/payment/shipping events via JDBC updates.

**Order Status Lifecycle:**
```
PENDING → INVENTORY_CHECKING → INVENTORY_RESERVED → PAYMENT_PROCESSING
       → PAYMENT_COMPLETED → SHIPPING_PROCESSING → SHIPPED → COMPLETED
                                                 → CANCELLED (inventory fail)
                                                 → FAILED (payment/shipping fail)
```

**n8n internal endpoint:**
- `GET /api/v1/orders/customer/{id}/purchased?productIds=...&since=...`

### Inventory Service · `8082`
Stock management in MongoDB. Reserves on order, releases on payment failure or shipping failure (saga compensation). Low-stock Kafka alerts.

### Payment Service · `8083`
Razorpay / Stripe / Mock adapters. Manual frontend-initiated flow or saga auto-process mode. Full refund support on shipping failure.

### Shipping Service · `8085`
Pre-payment delivery quotes with OSM geocoding + Uber H3 zone routing + Haversine hub distance. Creates shipments on `PaymentProcessedEvent`.

### Notification Service · `8084`
Transactional emails via Resend. PDF invoice auto-generated (OpenPDF) on every order. In-app notifications. 3-retry backoff on failures.

**n8n internal endpoint:**
- `POST /api/v1/notifications/internal/send` — send arbitrary email from automation

### Logging Service · `8092`
Aggregates structured logs from all Kafka topics. Cross-service `correlationId` tracing. 30-day MongoDB TTL auto-expiry.

### Analytics Service · `8093`
CQRS read-side. Batch Kafka consumer (~500 events/sec), dedicated PostgreSQL analytics DB, poison-pill DLQ, idempotency via `event_id`.

### Seller Service · `8091`
Merchant profiles, verification lifecycle (`PENDING → VERIFIED / REJECTED / SUSPENDED`), revenue analytics.

### Wishlist Service · `8090`
MongoDB wishlists. Compound unique index prevents duplicates. One-click move-to-cart.

---

## 🆕 Additional Services

### Sale Service · `8094`

Flash sale and promotion management service. Publishes `SaleStartedEvent` to Kafka so downstream services (cart, product, notification) can react to active promotions.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/sales` | Create a sale / promotion |
| GET | `/api/v1/sales` | List all active sales |
| GET | `/api/v1/sales/{id}` | Get sale by ID |

**Kafka:** Publishes → `sale-events`

---

### Location Service · `8095`

Browser geolocation → reverse geocoding → PostgreSQL persistence. Serves a minimal SPA that calls `navigator.geolocation`, submits coordinates to the backend, and displays the resolved address.

```
Browser → POST /api/location { lat, lng, accuracy }
        → OSM Nominatim reverse geocoding
        → UserLocationRepository (PostgreSQL)
```

| Method | Path | Description |
|---|---|---|
| POST | `/api/location` | Submit coordinates, get resolved address |
| GET | `/api/location/{userId}` | Get location history for a user |

---

### Scalable Notification Service (Go) · `8096`

High-throughput notification pipeline built in **Go** — a standalone service that complements the Java notification-service for bulk / priority-queued delivery scenarios.

**Stack:** Go · RabbitMQ (priority queues) · PostgreSQL · OpenTelemetry (tracing + metrics) · Prometheus

**Architecture:**
```
HTTP API → RabbitMQ priority queue → Worker pool → PostgreSQL
                                                 → Prometheus metrics
                                                 → OpenTelemetry spans
```

**Key features:**
- Priority queue routing — HIGH/MEDIUM/LOW channels with configurable RPM and worker counts
- OpenTelemetry distributed tracing — spans across HTTP ingestion, queue publish, and worker delivery
- Prometheus metrics exposed at `/metrics`
- `loadtest.exe` built-in for throughput benchmarking
- Docker Compose observability stack (Grafana + Prometheus + Jaeger)

---

## 🔁 n8n Automation (Abandoned Cart Recovery)

23-node n8n workflow that orchestrates abandoned-cart recovery without adding scheduler logic to Spring services.

**Flow:**
```
Schedule (5 min) → Get Abandoned Carts → Split → Get Customer Contact
→ Merge Cart + Customer → Check Order Status
→ IF Should Remind?
    TRUE  → Compose Reminder → Send Reminder Email → Mark Reminded → Wait 24h
          → Check Order Again → IF Purchased?
              FALSE → Build Coupon Email → Send Coupon → Mark Coupon Sent
              TRUE  → Stop (purchased after reminder)
    FALSE → Stop (already purchased)
```

**Quick start:**
```powershell
npx n8n
# Import: n8n/abandoned-cart-recovery.workflow.json
# Seed a test cart:
.\n8n\test-abandoned-cart.ps1 -Email you@yourdomain.com
```

**Internal endpoints used:**

| Service | Endpoint |
|---|---|
| cart-service | `GET /api/v1/cart/internal/abandoned?olderThanMinutes=30&stage=NONE` |
| cart-service | `PATCH /api/v1/cart/internal/{userId}/reminder-state` |
| cart-service | `POST /api/v1/cart/internal/{userId}/simulate-idle?minutes=45` |
| user-service | `GET /api/v1/users/internal/{userId}` |
| order-service | `GET /api/v1/orders/customer/{id}/purchased?productIds=...&since=...` |
| notification-service | `POST /api/v1/notifications/internal/send` |
| coupon-service | `POST /api/v1/coupons/generate?discountPercent=10` |

Reminder state (`NONE → REMINDED → COUPON_SENT`) stored on the cart in Redis — scheduled runs never double-send.

See [**n8n/README.md**](n8n/README.md) for full setup, n8n Cloud tunnel instructions, and test mode.

---

## 📨 Kafka Topics

| Topic | Producer | Consumers | Events |
|---|---|---|---|
| `order-events` | order-service | inventory-service, analytics-service, logging-service | `OrderCreatedEvent` |
| `inventory-events` | inventory-service | order-service, payment-service, logging-service | `InventoryReservedEvent`, `InventoryReservationFailedEvent` |
| `inventory-alerts` | inventory-service | ops / monitoring | Low-stock alerts |
| `payment-events` | payment-service | order-service, shipping-service, notification-service, logging-service | `PaymentProcessedEvent`, `PaymentFailedEvent` |
| `shipping-events` | shipping-service | order-service, notification-service, logging-service | `ShipmentProcessedEvent`, `ShipmentFailedEvent` |
| `sale-events` | sale-service | notification-service, product-service | `SaleStartedEvent` |
| `service-logs` | any service | logging-service | Structured log entries |
| `wishlist-events` | wishlist-service | future use | Wishlist activity |
| `order-analytics-dlq` | analytics-service | ops / monitoring | Poison pill events |

---

## ☸️ Kubernetes

Manifests in `/k8s`. order-service is the reference deployment.

**HPA — order-service**

| Property | Value |
|---|---|
| Min replicas | 2 |
| Max replicas | 8 |
| Scale-up trigger | CPU > 60% or Memory > 70% |
| Scale-up speed | +2 pods / 30s |
| Scale-down cooldown | 120s stabilization |

- Zero-downtime rolling update (`maxUnavailable: 0`, `maxSurge: 1`)
- `preStop` sleep 10s to drain connections before SIGTERM
- Startup, liveness, and readiness probes via `/actuator/health`
- Prometheus scraping annotations on pod template
- Pod anti-affinity to spread replicas across nodes

---

## 🔥 Load Testing Results

JMeter 5.6.3 · `POST /api/v1/orders` · 50 threads

| Run | Orders | Ramp-up | Throughput | P50 | P95 | P99 | Errors |
|---|---|---|---|---|---|---|---|
| Warm-up | 15,000 | 10s | 30.3 req/s | 20ms | 741ms | 3,374ms | 0% |
| Sustained | 15,000 | 100s | ~20 req/s | ~30ms | ~1,000ms | ~7,000ms | 0% |
| **Total** | **30,000** | | | | | | **0%** |

All 30,000 orders triggered the full Kafka saga end-to-end.

**Kafka Partition Scaling impact:**

| Partitions | Observed Processing Time |
|---|---|
| 1 | > 30 minutes |
| 5 | ~10 minutes |
| 10 | ~4 minutes (87% reduction) |

---

## 📊 Observability

All Spring services instrumented with Micrometer → Prometheus → Grafana.
Go scalable-notification-service uses OpenTelemetry → Prometheus + Jaeger.

| Tool | URL | Credentials |
|---|---|---|
| Prometheus | http://localhost:9091 | — |
| Grafana | http://localhost:3000 | admin / admin |
| Kafka UI | http://localhost:8069 | — |
| Alertmanager | http://localhost:9093 | — |

**Dashboard panels:** HTTP request rate, latency p50/p95/p99, Kafka consumer lag, JVM heap, GC pause time, HikariCP connections.

**Alert rules:** 19 rules across 6 groups — service availability, HTTP errors, JVM memory, threads, HikariCP, ingestion pipeline.

---

## 📦 Releases

Container images are published to GHCR on every push to `main` (`latest`, `sha-*`) and on every `v*` tag (`vX.Y.Z`, `vX.Y`). Full release notes live on the [GitHub releases page](https://github.com/zexxitywave/EventDrivenMesh/releases).

| Version | Date | Highlights |
|---|---|---|
| [v1.4.0](https://github.com/zexxitywave/EventDrivenMesh/releases/tag/v1.4.0) | 2026-11-10 | Pre-payment delivery quotes (OSM geocoding + Uber H3 + Haversine pricing) · coupon-service · n8n abandoned-cart recovery · sale-service · location-service · Go scalable-notification-service |
| [v1.3.0](https://github.com/zexxitywave/EventDrivenMesh/releases/tag/v1.3.0) | 2026-09-19 | Geocoding + Uber H3 delivery zones · shipping address carried through the saga · pgvector semantic search · CQRS read model · Chaos Monkey · Prometheus / Grafana / Alertmanager stack · first semver-tagged GHCR images |
| [v1.2.0](https://github.com/zexxitywave/EventDrivenMesh/releases/tag/v1.2.0) | 2026-09-02 | Security, resilience & platform: `/api/v1` versioning · transactional Outbox · Redis JWT blacklist · DLQ forwarding · OpenAPI docs · GHCR publishing pipeline |
| [v1.1.0](https://github.com/zexxitywave/EventDrivenMesh/releases/tag/v1.1.0) | 2026-07-30 | Maintenance re-tag of v1.0.0 (no code changes) |
| [v1.0.0](https://github.com/zexxitywave/EventDrivenMesh/releases/tag/v1.0.0) | 2026-07-30 | Initial release: Kafka saga, JWT API gateway, polyglot persistence, 16-service mesh |

---

## 🛠️ Tech Stack

| Category | Technology |
|---|---|
| **Languages** | Java 21, Go |
| **Frameworks** | Spring Boot 3.2.12, Spring Cloud 2023.0.2, Spring Security, Spring Data JPA |
| **Messaging** | Apache Kafka (KRaft), RabbitMQ |
| **Databases** | PostgreSQL 16, MongoDB, Redis 7, pgvector |
| **AI / Search** | pgvector HNSW, Ollama `nomic-embed-text` (768-dim) |
| **Payments** | Razorpay, Stripe (stub), Mock |
| **Automation** | n8n (abandoned-cart recovery workflow) |
| **Cloud** | AWS SES, Resend, OpenStreetMap Nominatim |
| **Observability** | Micrometer, Prometheus, Grafana, OpenTelemetry, Jaeger |
| **Infrastructure** | Docker Compose, Kubernetes (HPA), GitHub Actions CI |
| **Build** | Maven multi-module, Go modules |
| **Auth** | JWT (JJWT 0.12), Google OAuth2, Spring Security |
