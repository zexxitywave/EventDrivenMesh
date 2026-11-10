# FlashSale Concurrency Engineering

## Problem

The inventory reservation operation initially uses a simple
read-check-update approach.

The system must guarantee:

> Inventory must never be oversold, even when many users
> attempt to purchase the same product simultaneously.

## Baseline Implementation

Current flow:

Request
→ Read inventory
→ Check available quantity
→ Decrease available quantity
→ Increase reserved quantity
→ Save



## Experiment

We will reproduce the problem using concurrent requests.

### Scenario

Initial inventory:
- Available stock: 10
- Concurrent requests: 50
- Requested quantity per request: 1

### Expected Behavior

At most 10 requests should successfully reserve stock.

### Actual Behavior

To be measured using the concurrency test.

## Solutions To Evaluate

1. Baseline implementation
2. Optimistic locking using JPA @Version
3. Pessimistic database locking
4. Redis-based distributed locking/reservation

Each approach will be tested and compared.

## Metrics

We will record:

- Successful reservations
- Failed reservations
- Overselling occurrences
- Response time
- Throughput
- Locking/contention behavior

## Final Result
 ============================================================ 
filled after experiment haad done.

The concurrency experiments demonstrate that the implemented
locking and Redis strategies maintain inventory consistency under
concurrent requests.

No overselling was observed in the tested scenarios.

The Redis and pessimistic locking approaches successfully allowed
10 reservations from an initial stock of 10 under 50 concurrent
requests.
# Concurrency Experiments

## Experiment 1 — Optimistic Locking

### Setup

- Initial inventory: 10
- Concurrent requests: 50
- Quantity per request: 1
- Locking strategy: JPA Optimistic Locking
- Artificial delay: 100 ms

### Result

- Successful reservations: 5
- Failed reservations: 45
- Final available stock: 5
- Final reserved stock: 5
- Inventory invariant: 10

### Observation

Multiple requests attempted to modify the same inventory row concurrently.

JPA optimistic locking uses the inventory version column to ensure that an update only succeeds when the entity version is still current.

The database update contains:

UPDATE inventory
SET ..., version = ?
WHERE id = ?
AND version = ?

Therefore, stale transactions cannot overwrite a newer inventory state.

### Conclusion

Optimistic locking prevented inconsistent inventory updates and maintained the inventory invariant:

available stock + reserved stock = initial stock.

================================
## Experiment 2 — Pessimistic Locking

### Setup

- Initial inventory: 10
- Concurrent requests: 50
- Quantity per request: 1
- Locking strategy: JPA PESSIMISTIC_WRITE
- Artificial delay: 100 ms

### Result

- Successful reservations: 10
- Failed reservations: 40
- Final available stock: 0
- Final reserved stock: 10
- Inventory invariant: 10

### Observation

Pessimistic locking acquires a database row lock before modifying
the inventory.

Concurrent transactions attempting to modify the same inventory
row must wait for the lock to be released.

As a result, reservations were serialized and exactly the available
10 units were successfully reserved.

### Comparison

Optimistic locking:
- 5 successful reservations
- 45 failed due to concurrent version conflicts

Pessimistic locking:
- 10 successful reservations
- 40 failed after the available inventory was exhausted

### Trade-off

Optimistic locking avoids blocking but may require retries when
contention is high.

Pessimistic locking provides stronger serialization but can cause
transactions to wait for database locks and may reduce throughput
under heavy contention.


## Experiment 3 — Redis Atomic Reservation

### Setup

* Initial Redis stock: 10
* Concurrent requests: 50
* Quantity per request: 1
* Reservation mechanism: Redis Lua script

### Result

* Successful reservations: 10
* Failed reservations: 40
* Remaining Redis stock: 0
* Inventory invariant: 10
* Overselling: 0

### Observation

The Redis reservation operation successfully handled 50 concurrent
requests while only 10 units were available.

The reservation logic uses a Redis Lua script to perform the stock
check and decrement as one atomic operation.

The operation effectively performs:

GET stock
→ Check stock >= requested quantity
→ DECRBY stock quantity

Because these operations execute atomically inside the Lua script,
two concurrent requests cannot both pass the stock check for the
same unit.

### Conclusion

Redis atomic reservation prevented overselling under concurrent
access.

Exactly 10 of the 50 requests successfully reserved inventory,
while the remaining 40 requests failed because the available stock
was exhausted.

The final invariant was maintained:

available stock + successful reservations = initial stock

10 = 0 + 10

This demonstrates that Redis can provide a fast atomic reservation
layer suitable for high-concurrency flash-sale workloads.
