# Load Test Suite - Ecommerce Microservices

JMeter test plan covering all 12 active microservices through the API Gateway.

## Prerequisites

1. **JMeter 5.6+** — download from https://jmeter.apache.org/download_jmeter.cgi
2. **All services running** — `docker-compose up -d` + start each Spring Boot service
3. **Test user created** — register a user with the credentials below and verify the email

## Test User Credentials

```
Email:    loadtest@zexxity.online
Password: LoadTest@123
```
Create this user via `POST http://localhost:8080/api/auth/register` then verify OTP.

## How to Run

### Option A — JMeter GUI (recommended for first run)
1. Open JMeter
2. File → Open → select `ecommerce-load-test.jmx`
3. Update global variables at the top if needed (BASE_URL, THREADS, etc.)
4. Click the green Play button

### Option B — Command Line (for real metrics)
```bat
run-load-test.bat
```
Then open `results/html-report/index.html` in your browser.

## What Gets Tested

| Thread Group | Service | Endpoints Covered |
|---|---|---|
| 01 | Setup | Login + JWT extraction |
| 02 | Auth Service | GET /me, POST /refresh |
| 03 | Product Service | List, Search, GET by ID, Create |
| 04 | User Service | Profile CRUD, Address CRUD |
| 05 | Cart Service (Redis) | GET, Add item, Update qty |
| 06 | Wishlist Service (MongoDB) | Add, Get, Move to cart |
| 07 | Order Service (Saga) | Create order, Poll status, List |
| 08 | Inventory Service (MongoDB) | GET stock, Init stock |
| 09 | Payment Service | GET by order, Refund |
| 10 | Shipping Service | GET by order, Track |
| 11 | Notification Service | List notifications |
| 12 | Seller Service | Profile, Analytics, Products, Orders |
| 13 | Logging Service (MongoDB) | List logs, Filter by service |

## Default Load Config

| Parameter | Value |
|---|---|
| Virtual Users (threads) | 50 |
| Ramp-up period | 30 seconds |
| Loops per thread | 10 |
| Order saga threads | 20 (lower — saga is heavier) |
| Payment threads | 20 |
| Connect timeout | 5,000ms |
| Response timeout | 15,000ms |
| Cart response SLA | 500ms (Redis DurationAssertion) |

## Metrics You'll Get from HTML Report

- **Throughput** (requests/sec) per service and overall
- **90th / 95th / 99th percentile latency** per endpoint
- **Average / Min / Max response times**
- **Error rate %** per endpoint
- **Active threads over time** graph
- **Response time over time** graph
- **Bytes sent/received**

## Adjusting Load

Edit the global variables in JMeter under the Test Plan node:

| Variable | Default | Description |
|---|---|---|
| THREADS | 50 | Concurrent virtual users |
| RAMP_UP | 30 | Seconds to reach full load |
| LOOP_COUNT | 10 | Iterations per thread |
| BASE_URL | localhost | Gateway host |
| BASE_PORT | 8080 | Gateway port |
