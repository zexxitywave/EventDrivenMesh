# Kafka Connect

Kafka Connect runs as a Docker container on port `8073` and is visible inside **Kafka UI** under the "Kafka Connect" tab.

## Start

```bash
docker-compose up -d kafka-connect
```

First startup takes ~2 minutes — it downloads the JDBC connector plugin automatically.

## Check it's running

```bash
curl http://localhost:8073/connectors
```

Should return `[]` (empty list — no connectors deployed yet).

## Deploy the order-events sink connector

```bash
curl -X POST http://localhost:8073/connectors \
  -H "Content-Type: application/json" \
  -d @kafka-connect/connectors/order-events-sink.json
```

This will auto-create a table in `order_db` and stream every `order-events` Kafka message into PostgreSQL — no Java code needed.

## Check connector status

```bash
curl http://localhost:8073/connectors/order-events-sink/status
```

## Useful endpoints

| Method | URL | Description |
|---|---|---|
| GET | `/connectors` | List all connectors |
| POST | `/connectors` | Deploy a new connector |
| GET | `/connectors/{name}/status` | Check connector health |
| DELETE | `/connectors/{name}` | Remove connector |
| GET | `/connector-plugins` | List installed plugins |

## View in Kafka UI

Open http://localhost:8071 → select `kafka-cluster` → click **Kafka Connect** in the left sidebar.
You can deploy, pause, restart, and delete connectors from the UI without curl.
