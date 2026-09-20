# Memory — EventDrivenMesh

Persistent agent memory for this repo. Keeps project state, run instructions, conventions, and session history so any future session can get up to speed quickly.

---

## Snapshot

- **Repo:** zexxitywave/EventDrivenMesh · branch `main` · remote `origin` = https://github.com/zexxitywave/EventDrivenMesh
- **Stack:** Java 21 · Spring Boot 3.2.12 · Spring Cloud 2023.0.2 · Maven multi-module (16 modules: `common-library` + 15 services)
- **Pattern:** choreographed (not orchestrated) saga over Apache Kafka KRaft (Confluent cp-kafka 7.5.0); API Gateway routes all client traffic via Eureka `lb://`
- **Persistence:** 8 PostgreSQL DBs (auth, user, product+pgvector, seller, order, payment, shipping, analytics) · MongoDB (inventory, wishlist, notification, logging) · Redis (cart)
- **Docs split (recent):** `README.md` = usage/ops only · `architecture.md` = topology, HLD, saga, microservice catalog · `memory.md` = this file

## How to run

Infra (Docker Desktop must be running): `docker compose up -d kafka kafka-ui redis mongodb postgres`
(Bringing up all infra also starts prometheus, grafana, kafka-lag-exporter, alertmanager, nginx.)

- Build: `./mvnw clean install -DskipTests` (Windows: `mvnw.cmd`; repo uses Linux Maven wrapper `/scripts` too)
- Start order: **service-registry (Eureka) first**, then api-gateway, auth-service, then the rest in any order: `java -jar <svc>/target/<svc>-0.0.1-SNAPSHOT.jar`
- Prereq state: a `.env` in repo root holds DB/JWT/OAuth/Razorpay/SES/Resend/Ollama config (`.env` is gitignored); pgvector needs Ollama running (`ollama pull nomic-embed-text && ollama serve`) for semantic search backfill
- Verification tips: seed data via gateway, watch `%TEMP%\opencode\mesh-logs` for service logs during manual runs

## Services & Ports

| Service | Port | DB |
|---|---|---|
| service-registry (Eureka) | 8761 | — |
| api-gateway | 8080 | — |
| order-service | 8081 | order_db |
| inventory-service | 8082 | Mongo inventory |
| payment-service | 8083 | payment_db |
| notification-service | 8084 | Mongo notification_db |
| shipping-service | 8085 | shipping_db |
| auth-service | 8086 | auth_db |
| user-service | 8087 | user_db |
| product-service | 8088 | product_db (pgvector) |
| cart-service | 8089 | Redis |
| wishlist-service | 8090 | Mongo wishlist_db |
| seller-service | 8091 | seller_db |
| logging-service | 8092 | Mongo logging_db |
| analytics-service | 8093 | analytics_db |

Infra ports: Kafka `9095` (ext) / `29092` (internal) · Kafka UI `8069` · Prometheus `9091` · Grafana `3000` (admin/admin) · kafka-lag-exporter `8000` · Alertmanager `9093` · nginx edge `9080` · Postgres `5432` · Mongo `27017` · Redis `6379`

## Saga / Event Flow

`order-events → inventory-events → payment-events → shipping-events → notification (email + PDF invoice)`

- Chain: order-service publishes `OrderCreatedEvent` → inventory reserves (reserved/failed) → payment captures (processed/failed) → shipping ships (processed/failed) → notification emails.
- order-service is the saga **state tracker** (`PENDING → … → SHIPPED`, `CANCELLED`/`FAILED` branches); it consumes inventory/payment/shipping events.
- Analytics = CQRS projection of `order-events`; poison events → `order-analytics-dlq`.
- Logging consumes all topics + `service-logs`; 30-day TTL; trace by `correlationId`.
- Payment modes: `payment.saga-auto-process=true` (mock, auto-advance) vs `false` (real Razorpay: pre-create payment row on reservation, idempotent `/initiate`, `/verify` or webhook).

## Delivery Charge (recent feature)

- At order creation, order-service calls shipping-service `POST /api/v1/shipping/quotes` (sync, Eureka `lb://`) and stores `deliveryCharge`; `totalAmount = items + deliveryCharge` so payment captures it in one amount.
- Pricing (shipping-service `shipping.pricing.*`, INR): `baseFee(49.00) + perKm(1.50) × distanceToNearestHub`, capped at `maxCharge(549.00)`; no geocode → `fallbackCharge(99.00)`.
- Fallback in order-service if quote fails: `order.shipping.fallback-delivery-charge` (99.00), order creation never breaks.
- Shipping internally geocodes (OSM Nominatim) → H3 zone → nearest hub → carrier/ETA; persists `deliveryCharge` + tracking.
- End-to-end verified (orders: Bengaluru → total 53.45, Delhi → total 52.45), read model also returns `deliveryCharge`.

## Releases / CI

- GHCR images published on `main` pushes (`latest`/`sha-*`) and tag pushes (`{{raw}}`, `v{{major}}.{{minor}}`). CI (`ci.yml`) now uses `fetch-depth: 0` + trigger `tags: ['v*']` + tag-push check on docker-publish. Earlier releases v1.0.0–v1.2.0 never got semver images (no tag-trigger back then).
- **v1.3.0** released; all 15 images verified on GHCR with `v1.3.0`/`v1.3`. Tag `v1.3.0` points at `c7db1d9`. Release page on GitHub is drafted by the user (not the agent).
- Next release should be **v1.4.0** (delivery-charge feature `13d3667` is not tagged; latest images on `main` already contain it).

## Recent Commits (newest first)

```
11e195f docs: document pre-payment delivery quote feature          (pushed)
13d3667 feat: quote delivery charge at order time and include payment total  (pushed)
c7db1d9 ci: build semver-tagged images on version tag pushes
9368f68 ci: fetch git tags so release images get semver tags
e37408f ci: publish v1.3.0 images
c9cdf7c feat: seed catalog stock in inventory, bulk-order count, AWS deploy configs
8abfc22 chore: stop tracking runtime logs, gitignore run-logs/
... (earlier: shippingAddress carried through saga events, H3 geocoding, etc.)
```

## Session State

- Infra containers **currently running** (started last session): kafka, kafka-ui, redis, mongodb, postgres — all healthy.
- 5 JVM services (registry, order, inventory, payment, shipping) were left running during the delivery-charge verification; re-check before relying on endpoints.
- README split into `architecture.md` + `memory.md` is **uncommitted** (working tree only).

## Conventions / Guardrails

- Commit style: `feat:`, `fix:`, `docs:`, `ci:`, `chore:` lowercase with a short imperative summary (see git log).
- Never add code comments unless asked; mirror existing style (Lombok, MapStruct, Spring Boot conventions).
- Only commit/push when the user explicitly asks.
- Writable docs: README (usage), architecture.md (design), memory.md (agent memory) — keep them current per change.
- Tests: system was validated end-to-end manually (JMeter load tests live in `load-tests/`); LOAD_TESTING results documented in README.