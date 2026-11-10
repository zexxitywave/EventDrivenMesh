# FlashSale Inventory Engine — Architecture

## 1. Overview

FlashSale Inventory Engine is a Spring Boot backend designed to handle
high-concurrency inventory reservation requests.

The system combines:

- Spring Boot
- PostgreSQL
- JPA / Hibernate
- Redis
- Apache Kafka
- Spring Kafka
- Spring Boot Actuator

The main goal is to prevent inventory overselling while supporting
concurrent reservation requests and asynchronous event processing.

---

## 2. High-Level Architecture

```text
                         Client
                           |
                           v
                +----------------------+
                | InventoryController  |
                +----------+-----------+
                           |
             +-------------+-------------+
             |                           |
             v                           v
     Direct Inventory APIs        Kafka Reservation API
             |                           |
             v                           v
     InventoryService          InventoryEventProducer
             |                           |
             v                           v
        PostgreSQL                  Apache Kafka
                                         |
                                         v
                              InventoryEventConsumer
                                         |
                              +----------+----------+
                              |                     |
                              v                     v
                       ProcessedEvent          InventoryService
                         Repository                 |
                              |                     |
                              v                     v
                       PostgreSQL              PostgreSQL