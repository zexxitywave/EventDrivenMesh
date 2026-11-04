<div align="center">

# Jagdish Kumar Sahu

**Backend Engineer · Distributed Systems · Cloud-Native Java**

[![Profile Views](https://komarev.com/ghpvc/?username=zexxitywave&style=flat-square&color=0d1117&label=Profile+Views)](https://github.com/zexxitywave)

</div>

---

## About

Backend engineer with a focus on distributed systems, event-driven architecture, and production-grade Java. I build systems designed for real load — not just demos. Most of my work involves Kafka-orchestrated microservices, cloud-native deployments, and full observability from the start.

Currently building **Zexxity** — a complete e-commerce platform backed by a 16-service Kafka mesh with saga orchestration, live Razorpay payments, pgvector semantic search, and a Prometheus/Grafana observability stack.

---

## Projects

### [EventDrivenMesh](https://github.com/zexxitywave/EventDrivenMesh)
> Event-driven e-commerce backend — 16 microservices, Kafka Saga, CQRS analytics, Kubernetes HPA

Production-grade microservices platform built on Spring Boot 3 and Java 21. Services communicate asynchronously via Apache Kafka using a choreography-based saga pattern with full compensation on failure. A central API Gateway handles JWT validation and load-balanced routing via Eureka.

**Key capabilities**
- Kafka Saga with automatic compensating rollbacks across inventory → payment → shipping
- Pre-payment delivery quoting — shipping-service geocodes the order address, routes via H3 zones, assigns carrier and ETA, and prices delivery before the customer pays
- Semantic product search — pgvector with HNSW cosine index and Ollama `nomic-embed-text` (768-dim) embeddings; EmbeddingInitializer backfills at startup
- PDF invoice generation — auto-generated on every order, attached to confirmation email, stored in MongoDB, downloadable anytime
- CQRS analytics — dedicated PostgreSQL analytics DB, batch Kafka consumer at ~500 events/sec, poison pill detection with DLQ
- Kubernetes HPA — CPU/memory scaling, zero-downtime rolling updates, pod anti-affinity, liveness/readiness probes
- Full observability — Micrometer → Prometheus → Grafana (HTTP latency p50/p95/p99, Kafka consumer lag, JVM heap, HikariCP pool)
- Load tested: 30,000 orders across two JMeter runs, 0% error rate, P50 < 30ms

**Stack** `Java 21` `Spring Boot 3` `Apache Kafka` `PostgreSQL` `MongoDB` `Redis` `pgvector` `Ollama` `Razorpay` `Docker` `Kubernetes` `Prometheus` `Grafana` `GitHub Actions`

---

### [EnerFlux](https://github.com/zexxitywave/EnerFlux)
> Production-grade IoT energy monitoring — 606 msg/s peak, 1.09M Kafka messages, 0% error rate

Real-time household energy monitoring system built with Spring Boot 4 and Java 21. Ingests device readings through a 3-stage Kafka pipeline, persists to InfluxDB time-series, classifies WARNING/CRITICAL alerts via 1-hour rolling aggregations, and delivers billing projections via async email.

**Key capabilities**
- 3-stage Kafka pipeline: ingestion-service → usage-service (InfluxDB) → alert-service (email)
- 606 req/s peak ingestion, 1,091,258 total Kafka messages, 129,500+ InfluxDB data points, 0 failures
- Manual Kafka ack only after InfluxDB write — zero data loss under broker or DB downtime
- 5-second rolling aggregation scheduler with 1-hour per-user alert cooldown
- AWS S3 complaint uploads — parallel `CompletableFuture` processing, 1,450 requests at 0% error
- Chaos tested: killed Kafka, InfluxDB, and ingestion-service mid-load — all recovered without data loss
- 4 Grafana dashboards (33 panels), 19 Prometheus alert rules, custom Micrometer counters
- JVM profiled under load: heap at 3%, GC pauses at 0.09ms avg, 0 blocked threads

**Stack** `Java 21` `Spring Boot 4` `Apache Kafka` `InfluxDB` `PostgreSQL` `AWS S3` `Keycloak` `Resilience4j` `Prometheus` `Grafana` `Docker`

---

### [invoice-processing](https://github.com/zexxitywave/invoice-processing)
> Serverless AI-powered invoice automation — AWS Lambda + Textract + Bedrock, human-in-the-loop review

Fully serverless invoice processing pipeline on AWS (ap-south-1). Vendors email PDFs to `invoices@zexxity.online` — SES ingests them, EventBridge triggers the Lambda pipeline, Textract extracts fields with per-field confidence scores, Bedrock Nova-Lite validates and risk-scores, and a deterministic rule engine auto-approves or routes to human review.

**Key capabilities**
- Textract `AnalyzeExpense` with confidence scoring; retries with jittered backoff on rate limits
- Auto-approval only when: confidence ≥ 95%, no missing fields, line-item math checks out, Bedrock agrees
- Human-in-the-loop — review dashboard + one-click approve/reject via 72-hour signed email tokens
- Idempotent extraction — dedupes on `invoiceId`/`objectKey`, safe for EventBridge at-least-once delivery
- 10 Lambda functions: extraction, SES ingest, review, token approval, upload URL, daily digest, escalation cleanup, S3 cleanup, warm-up
- React 19 frontend on AWS Amplify — dashboard, upload, review queue, full audit log with CSV export
- Daily digest and 72-hour escalation emails via Brevo

**Stack** `Java 21` `AWS Lambda` `Amazon Textract` `Amazon Bedrock (Nova-Lite)` `Amazon DynamoDB` `Amazon SES` `AWS S3` `API Gateway` `React 19` `AWS Amplify` `Brevo` `SAM / CloudFormation`

---

## Tech Stack

**Languages**

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Python](https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-3178C6?style=flat-square&logo=typescript&logoColor=white)

**Backend & Messaging**

![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![Apache Kafka](https://img.shields.io/badge/Apache_Kafka-231F20?style=flat-square&logo=apachekafka&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-6DB33F?style=flat-square&logo=spring&logoColor=white)

**Databases**

![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![MongoDB](https://img.shields.io/badge/MongoDB-47A248?style=flat-square&logo=mongodb&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-DC382D?style=flat-square&logo=redis&logoColor=white)
![InfluxDB](https://img.shields.io/badge/InfluxDB-22ADF6?style=flat-square&logo=influxdb&logoColor=white)
![DynamoDB](https://img.shields.io/badge/DynamoDB-4053D6?style=flat-square&logo=amazondynamodb&logoColor=white)

**Cloud & Infrastructure**

![AWS](https://img.shields.io/badge/AWS-232F3E?style=flat-square&logo=amazonaws&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat-square&logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-326CE5?style=flat-square&logo=kubernetes&logoColor=white)

**Observability**

![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat-square&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat-square&logo=grafana&logoColor=white)

---

## GitHub Stats

<div align="center">
  <img height="165" src="https://github-readme-stats.vercel.app/api?username=zexxitywave&show_icons=true&theme=github_dark&hide_border=true&count_private=true&include_all_commits=true" />
  <img height="165" src="https://github-readme-stats.vercel.app/api/top-langs/?username=zexxitywave&layout=compact&theme=github_dark&hide_border=true&langs_count=6" />
</div>

---

## Contact

Open to backend engineering roles focused on distributed systems, platform engineering, or cloud-native Java.

[![GitHub](https://img.shields.io/badge/GitHub-zexxitywave-181717?style=flat-square&logo=github)](https://github.com/zexxitywave)
