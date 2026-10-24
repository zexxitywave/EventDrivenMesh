# EventDrivenMesh

## Overview

EventDrivenMesh is a production-grade, event-driven e-commerce backend built with Spring Boot microservices. Services communicate asynchronously via Apache Kafka using the choreography-based saga pattern, and synchronously via REST through a central API Gateway with JWT authentication.

> "Polyglot Persistence · Choreography-Based Saga · Zero-Downtime Kubernetes Deployments"

[![CI](https://github.com/zexxitywave/EventDrivenMesh/actions/workflows/ci.yml/badge.svg)](https://github.com/zexxitywave/EventDrivenMesh/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-ED8B00)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.12-6DB33F)](https://spring.io/projects/spring-boot)

---

## Project Structure

```
EventDrivenMesh/
├── pom.xml                      # Parent POM — Java 21, Spring Boot 3.2.12
├── docker-compose.yml           # Kafka, PostgreSQL, MongoDB, Redis, Kafka UI
├── .env                         # Environment variables
├── scripts/                     # pgvector embedding & benchmark scripts
├── k8s/                         # Kubernetes manifests
├── monitoring/                  # Prometheus & Grafana config
├── common-library/              # Shared Kafka events & DTOs
├── service-registry/            # Eureka server
├── api-gateway/                 # Spring Cloud Gateway + JWT filter
├── auth-service/                # Authentication, JWT, OAuth2
├── user-service/                # User profiles & addresses
├── product-service/             # Product catalog, semantic search (pgvector)
├── seller-service/              # Merchant management & KYC verification
├── cart-service/                # Redis shopping cart
├── wishlist-service/            # MongoDB wishlists
├── order-service/               # Orders + saga state machine
├── inventory-service/           # Stock management
├── payment-service/             # Razorpay / Mock payments
├── shipping-service/            # Shipment tracking with Uber H3 geocoding
├── notification-service/        # Email + in-app notifications + PDF invoices
├── logging-service/             # Centralized log aggregation (30-day TTL)
└── analytics-service/           # CQRS analytics — batch Kafka consumer + REST API
```

---

## Features Implemented

### 1. Event-Driven Choreography Saga
- **No central orchestrator** — each service reacts to one Kafka event and publishes the next, forming a chain: `order → inventory → payment → shipping → notification`
- **Compensating events** — stock unavailable cancels the order immediately; payment or shipping failure triggers rollback events
- **Idempotency** — every event carries `correlationId` + `eventId`; a unique `event_id` constraint in the analytics DB prevents duplicate ingestion under replay or consumer restarts

### 2. API Gateway & Security
- **JWT Authentication** — validated once at the gateway (HMAC-SHA256, 15-min access / 7-day rotated refresh); downstream services receive trusted `X-User-Id`, `X-User-Email`, `X-User-Role` headers — no repeated token parsing
- **Google OAuth2** — federated login via Spring Security OAuth2 client
- **IP-based rate limiting** — via Redis at the gateway level
- **Friendly error pages** — HTML 4xx/5xx responses served from the gateway

### 3. Polyglot Persistence
- **PostgreSQL 16** — transactional state for auth, user, product, seller, order, payment, shipping, analytics (8 isolated databases)
- **MongoDB** — document storage for inventory, wishlist, notification, logging
- **Redis** — ephemeral session cart

### 4. Semantic Product Search (pgvector)
- `product-service` uses a `vector(768)` column with an HNSW cosine-distance index
- `EmbeddingInitializer` backfills embeddings at startup via Ollama `nomic-embed-text` (768 dimensions)
- `GET /api/v1/products/search?keyword=...` triggers semantic vector search; traditional ILIKE keyword fallback also available

### 5. Payment Integration
- **Razorpay** — real gateway orders, captures, signed webhook verification
- **Mock mode** — `payment.saga-auto-process=true` collapses the checkout flow for demos and CI, no browser required
- **Idempotent initiate** — `POST /api/v1/payments/initiate` resumes the pre-created payment row, preserving the saga `correlationId`

### 6. PDF Invoice Generation
- On every successful order, the notification service auto-generates a styled PDF invoice via OpenPDF and emails it via Resend
- Invoices are stored as binary in MongoDB and downloadable via `GET /api/v1/notifications/invoice/{orderId}`

### 7. Analytics (CQRS)
- Dedicated `analytics-service` with its own PostgreSQL database — never touches transactional DBs
- Batch Kafka consumer for `order-events` delivering ~500 events/sec throughput
- Tracks revenue, top customers, and daily order trends
- **Poison Pill + DLQ** — malformed events routed to `order-analytics-dlq` for safe reprocessing

### 8. Full Observability
- **Prometheus + Grafana** — Micrometer-instrumented services expose HTTP latency p50/p95/p99, Kafka consumer throughput, JVM heap, HikariCP pool metrics
- **Centralized logging** — `logging-service` aggregates all business events with cross-service `correlationId` tracing and a 30-day TTL
- **kafka-lag-exporter** — consumer lag surfaced as Prometheus metrics

### 9. Kubernetes Ready
- HPA-configured with CPU/memory autoscaling (2–8 replicas)
- Zero-downtime rolling updates with `preStop` drain hooks
- Liveness/readiness probes and Prometheus scraping out of the box

### 10. CI/CD
- GitHub Actions pipeline builds all 16 Maven modules on every push

---

## Order Saga Flow

The checkout flow uses a choreography-based saga — no central orchestrator. Each service reacts to events and publishes the next step.

```
Client
  └─► order-service (OrderCreatedEvent)
        └─► inventory-service
              ├─ [Stock available] InventoryReservedEvent
              │     └─► payment-service
              │           ├─ [Payment success] PaymentProcessedEvent
              │           │     ├─► shipping-service (ShipmentProcessedEvent)
              │           │     │     └─► notification-service (email + PDF)
              │           │     └─► order-service (status: SHIPPED)
              │           └─ [Payment failed] PaymentFailedEvent
              │                 └─► order-service (status: FAILED)
              └─ [Stock unavailable] InventoryReservationFailedEvent
                    └─► order-service (status: CANCELLED)
```

**Order status machine:** `PENDING → INVENTORY_RESERVED → PAYMENT_COMPLETED → SHIPPED` (or `CANCELLED` / `FAILED` on compensation)

---

## Quick Start

### Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Docker Desktop | Latest |
| Maven | 3.9+ |
| Ollama | Latest (optional — for semantic search) |

### 1. Clone the Repository

```bash
git clone https://github.com/zexxitywave/EventDrivenMesh.git
cd EventDrivenMesh
```

### 2. Configure Environment

Create a `.env` file in the project root:

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USER=postgres
DB_PASSWORD=postgres123

# JWT — HMAC-SHA256 Base64-encoded secret
JWT_SECRET=your_jwt_secret_here

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Razorpay
RAZORPAY_KEY_ID=your_razorpay_key_id
RAZORPAY_KEY_SECRET=your_razorpay_key_secret

# Email — auth-service (AWS SES)
MAIL_USERNAME=your_ses_smtp_username
MAIL_PASSWORD=your_ses_smtp_password

# Email — notification-service (Resend)
RESEND_API_KEY=your_resend_api_key

# Ollama (optional — for pgvector semantic embeddings)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_EMBEDDING_MODEL=nomic-embed-text
```

### 3. Start Infrastructure

```bash
docker-compose up -d
```

This starts Kafka (KRaft, no ZooKeeper), PostgreSQL 16 with pgvector and 8 auto-created databases, MongoDB, Redis, Kafka UI, Prometheus, Grafana, kafka-lag-exporter, and Alertmanager.

### 4. Build All Modules

```bash
./mvnw clean install -DskipTests
```

### 5. Start Services

Start in this order to ensure Eureka is ready first:

```
1. service-registry   ← Eureka must be up first
2. api-gateway
3. auth-service
4. All remaining services (any order)
```

> **Semantic search?** Start Ollama before `product-service`:
> ```bash
> ollama pull nomic-embed-text
> ollama serve
> ```

> **Kafka reset on Windows?** Run `FIX-KAFKA.ps1` in PowerShell.

---

## Service Port Reference

| Service | Port | Database |
|---|---|---|
| service-registry | `8761` | — |
| api-gateway | `8080` | — |
| auth-service | `8086` | PostgreSQL `auth_db` |
| user-service | `8087` | PostgreSQL `user_db` |
| product-service | `8088` | PostgreSQL `product_db` (pgvector) |
| seller-service | `8091` | PostgreSQL `seller_db` |
| cart-service | `8089` | Redis |
| wishlist-service | `8090` | MongoDB `wishlist_db` |
| order-service | `8081` | PostgreSQL `order_db` |
| inventory-service | `8082` | MongoDB `inventory` |
| payment-service | `8083` | PostgreSQL `payment_db` |
| shipping-service | `8085` | PostgreSQL `shipping_db` |
| notification-service | `8084` | MongoDB `notification_db` |
| logging-service | `8092` | MongoDB `logging_db` |
| analytics-service | `8093` | PostgreSQL `analytics_db` |
| Kafka UI | `8069` | — |
| Prometheus | `9091` | — |
| Grafana | `3000` | — |

All services register with Eureka and are reachable through the API Gateway at `http://localhost:8080`.

---

## API Endpoints

### Auth Service (`/api/v1/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/register` | Public | Register a new user |
| `POST` | `/login` | Public | Login, returns JWT |
| `POST` | `/refresh` | Public | Refresh access token |
| `GET` | `/oauth2/authorize/google` | Public | Start Google OAuth2 |

### User Service (`/api/v1/users`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/profile` | JWT | Get current user profile |
| `PUT` | `/profile` | JWT | Update profile |
| `POST` | `/address` | JWT | Add address |

### Product Service (`/api/v1/products`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | Public | List all products (ILIKE keyword filter) |
| `GET` | `/search?keyword=` | Public | Semantic vector search (pgvector) |
| `GET` | `/{id}` | Public | Get product by ID |
| `POST` | `/` | Seller | Create product |
| `PUT` | `/{id}` | Seller | Update product |

### Order Service (`/api/v1/orders`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/` | JWT | Place an order (triggers saga) |
| `GET` | `/` | JWT | List user's orders |
| `GET` | `/{id}` | JWT | Get order by ID |

### Payment Service (`/api/v1/payments`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/initiate` | JWT | Initiate Razorpay payment (idempotent) |
| `POST` | `/verify` | JWT | Verify payment signature |
| `POST` | `/webhook` | Public | Razorpay signed webhook |
| `POST` | `/refund/{orderId}` | Admin | Initiate refund |

### Notification Service (`/api/v1/notifications`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/` | JWT | List user's notifications |
| `GET` | `/invoice/{orderId}` | JWT | Download PDF invoice |

### Logging Service (`/api/v1/logs`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/trace/{correlationId}` | Admin | Full saga trace by correlationId |

### Analytics Service (`/api/v1/analytics`)

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/revenue` | Admin | Revenue summary |
| `GET` | `/top-customers` | Admin | Top customers by spend |
| `GET` | `/orders/daily` | Admin | Daily order trends |

---

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `order-events` | order-service | inventory-service, analytics-service, logging-service |
| `inventory-events` | inventory-service | order-service, payment-service, logging-service |
| `payment-events` | payment-service | order-service, shipping-service, notification-service, logging-service |
| `shipping-events` | shipping-service | order-service, notification-service, logging-service |
| `service-logs` | all services | logging-service |
| `order-analytics-dlq` | analytics-service | — |

---

## Configuration

All values can be set in `.env` or passed as environment variables.

| Variable | Description |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | HMAC-SHA256 Base64-encoded secret |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | Razorpay payment gateway |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | AWS SES SMTP for auth emails |
| `RESEND_API_KEY` | Resend API for notification emails |
| `OLLAMA_BASE_URL`, `OLLAMA_EMBEDDING_MODEL` | Ollama for pgvector embeddings |

---

## Load Testing Results

Load tested with JMeter — **0.00% errors across 30,000 orders**.

| Metric | Result |
|---|---|
| Throughput | ~30 req/s sustained on order placement |
| Latency p50 | ~20–30 ms |
| Latency p95 | ~1 s |
| Latency p99 | Stable under sustained load |
| Kafka partitions 1 → 10 | 87% latency reduction |

---

## Observability

Prometheus scrapes all services. Grafana dashboards at `http://localhost:3000` (admin / admin).

### Grafana Dashboard Panels

| Panel | Metric |
|---|---|
| HTTP Request Rate | Requests per second across all services |
| HTTP Latency p50 / p95 / p99 | `http_server_requests_seconds` percentiles |
| Kafka Consumer Lag | Messages behind per topic / consumer group |
| Kafka Throughput | Events ingested per second (analytics consumer) |
| JVM Heap Usage | `jvm_memory_used_bytes` — heap vs. non-heap |
| JVM GC Pause Time | Garbage collection pause duration |
| HikariCP Active Connections | Active DB connections per service |
| HikariCP Pending Threads | Connection pool saturation indicator |

---

## Tech Stack

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.12, Spring Cloud 2023.0.2 |
| Service Discovery | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Messaging | Apache Kafka (KRaft, no ZooKeeper) |
| Relational DB | PostgreSQL 16 (8 isolated databases) |
| Vector Search | pgvector — `vector(768)` HNSW cosine index |
| Embedding Model | Ollama `nomic-embed-text` (768 dimensions) |
| Document DB | MongoDB |
| Cache | Redis 7 |
| Auth | JWT (JJWT 0.12), Spring Security, Google OAuth2 |
| Payment | Razorpay, Mock adapter |
| Email | AWS SES (auth), Resend (notifications) |
| PDF Generation | OpenPDF / iText |
| Observability | Micrometer, Prometheus, Grafana |
| CI/CD | GitHub Actions |
| Build | Maven multi-module |
| Containerization | Docker Compose, Kubernetes (HPA) |
| Geocoding | Uber H3 (shipping delivery zones) |

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

<div align="center">
Built with ♥ by <a href="https://github.com/zexxitywave">zexxitywave</a> · Spring Boot · Apache Kafka · Java 21
</div>
