# Testing

## Concurrency Testing

The inventory system was tested with 50 concurrent reservation
requests against an initial stock of 10 units.

### Optimistic Locking

- Concurrent requests: 50
- Successful reservations: 5
- Failed reservations: 45
- Final available stock: 5
- Final reserved stock: 5
- Invariant: 10

### Pessimistic Locking

- Concurrent requests: 50
- Successful reservations: 10
- Failed reservations: 40
- Final available stock: 0
- Final reserved stock: 10
- Invariant: 10

### Redis Atomic Reservation

- Concurrent requests: 50
- Successful reservations: 10
- Failed reservations: 40
- Remaining Redis stock: 0
- Invariant: 10
- Overselling: 0

## Kafka Testing

Kafka reservation events were tested for:

- Successful event processing
- Duplicate event detection
- Retry handling
- Dead Letter Topic processing

Duplicate events are tracked using a unique `eventId` and the
`processed_events` table.

## Metrics Testing

The following Actuator metrics were verified:

- `flashsale.kafka.processed`
- `flashsale.kafka.duplicate`
- `flashsale.reservation.failure`

Observed during testing:

- Kafka processed events: 2
- Kafka duplicate events: 4
- Reservation failure count: 11

## Health Testing

Spring Boot Actuator health checks confirmed:

- PostgreSQL: UP
- Redis: UP
- Liveness: UP
- Readiness: UP

## Build Verification

Final Maven test execution:

- Tests run: 6
- Failures: 0
- Errors: 0
- Skipped: 0
- Build: SUCCESS