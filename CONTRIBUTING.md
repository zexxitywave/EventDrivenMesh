# Contributing

## Prerequisites

- Java 21
- Docker Desktop
- Maven 3.9+

## Running Locally

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Build all modules
./mvnw clean install -DskipTests

# 3. Start services in IntelliJ or via Maven
# Start service-registry first, then api-gateway, then the rest
```

## Running Tests

```bash
./mvnw test
```

## Branch Naming

```
feat/your-feature-name
fix/bug-description
chore/maintenance-task
```

## Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org):

```
feat: add payment webhook support
fix: remove duplicate methods in NotificationController
chore: update dependencies
docs: update README with analytics-service
```

## Code Style

- Java 21 with Lombok — no boilerplate getters/setters
- One service = one database (polyglot persistence)
- All inter-service async communication via Kafka events
- Synchronous REST only for read queries between services
