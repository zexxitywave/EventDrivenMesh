# FlashSale Inventory Engine

A backend inventory reservation system built with Java and Spring Boot,
designed to handle high-concurrency flash-sale inventory requests
without overselling.

The project demonstrates database concurrency control, Redis atomic
operations, asynchronous Kafka processing, event idempotency, and
application monitoring.

## Tech Stack

- Java
- Spring Boot
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis
- Apache Kafka
- Maven
- Docker
- Spring Boot Actuator

## Key Features

- Inventory creation and stock management
- Concurrent inventory reservation
- Optimistic locking using JPA `@Version`
- Pessimistic database locking using `PESSIMISTIC_WRITE`
- Atomic Redis inventory reservation using Lua scripts
- Asynchronous reservation requests using Apache Kafka
- Kafka duplicate-event detection and idempotent processing
- Kafka retry handling
- Inventory release and stock reset
- Application health and custom metrics using Spring Boot Actuator

## Concurrency Handling

The main problem addressed by this project is preventing inventory
overselling when multiple users try to reserve the same product
simultaneously.

Example test scenario:

- Initial inventory: 10
- Concurrent requests: 50
- Quantity per request: 1

The system was tested using multiple concurrency-control strategies.

### Optimistic Locking

JPA optimistic locking uses the inventory version field to detect
concurrent updates.

Test result:

- Concurrent requests: 50
- Successful reservations: 5
- Failed reservations: 45
- Available stock: 5
- Reserved stock: 5
- Inventory invariant: 10

### Pessimistic Locking

Pessimistic locking uses a database row lock so that concurrent
transactions cannot modify the same inventory row simultaneously.

Test result:

- Concurrent requests: 50
- Successful reservations: 10
- Failed reservations: 40
- Available stock: 0
- Reserved stock: 10
- Inventory invariant: 10

### Redis Atomic Reservation

Redis is used as a fast reservation layer with a Lua script that
performs the stock check and decrement atomically.

Test result:

- Concurrent requests: 50
- Successful reservations: 10
- Failed reservations: 40
- Remaining Redis stock: 0
- Overselling: 0

## Kafka Event Processing

Reservation requests can be published asynchronously to Kafka.

Kafka topic:

`inventory-reservations`

Each reservation event contains:

```json
{
  "eventId": "unique-event-id",
  "productId": 1,
  "quantity": 1
}