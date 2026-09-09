# Event-Driven Mesh - Architecture Journal

Incremental design notes recorded as the mesh evolved, one commit per entry.


## 1. Scaffold the workspace

Initialize the monorepo layout with one module per service, a shared common-library for event contracts and cross-service DTOs, and docker-compose for all local infrastructure.

---


## 2. Shared event contracts

Introduce common-library holding BaseEvent (eventId, correlationId, timestamp), typed Kafka publishers and consumers, plus the error envelope shared by every REST controller.

---
