# Scalable Notification Service

## Overview

The Scalable Notification Service is designed to handle high volumes of notifications efficiently, with a focus on reliability, prioritization, and rate-limiting. It supports multiple notification channels like Email, SMS, and Push notifications, and processes them based on priority levels (High, Medium, Low).

## Project Structure

```
/config                  # Configuration files
/internal                # Private application and library code
.env                     # Environment configuration
.gitignore               # Git ignore rules
go.mod                   # Go module file
go.sum                   # Go dependencies
main.go                  # Main entry point of the application
README.md                # Project documentation
```

### Directory Details

- **/config**: Stores configuration files that are necessary for the application to run, including environment variables.
- **/internal**: Contains private application and library code that should not be exposed outside of this project.
- **main.go**: The main entry point of the application, where the server and worker pools are initialized.

## Features Implemented

### 1. Initialization and Setup
- **Basic HTTP Server Setup**: Using the Fiber framework, a simple HTTP server has been initialized with routes for the root path and a health check.
- **Environment Variables**: Configuration is managed using a `.env` file, ensuring that sensitive information is kept secure.

### 2. Message Queue Integration
- **RabbitMQ**: Implemented RabbitMQ for message queuing with different queues for different notification channels and priorities.
- **Producer Logic**: Notifications are sent to different queues based on their channel (e.g., Email, SMS, Push) and priority (e.g., Low, Medium, High).

### 3. Worker Pools and Rate Limiting
- **Worker Pools**: Defined worker pools for each queue, with the ability to reallocate workers based on queue activity.
- **Rate Limiting**: Implemented rate limiting for each worker to ensure external services are not overwhelmed.

### 4. Error Handling and Retry Mechanism
- **Retry Logic**: If a notification fails to send, it is retried based on specific rules. Unsuccessful notifications are logged to PostgreSQL for further analysis.
- **Logging**: Implemented comprehensive logging for tracking errors and system performance.

### 5. Worker Reallocation
- **Dynamic Worker Allocation**: Workers are dynamically allocated to different queues based on their load and priority. Idle queues have their workers reallocated to active queues.
- **Scheduler**: A scheduler balances the priority of queues, ensuring that high-priority notifications are processed promptly without overwhelming any single priority level.

## Future Scope

### 1. Real-time Monitoring with Prometheus and Grafana
- **Metrics Collection**: Integration with Prometheus for collecting metrics related to the performance of the notification service.
- **Visualization**: Setting up Grafana dashboards for real-time monitoring and visualization of key metrics, such as message throughput, worker utilization, and error rates.

## API Endpoints

| Method   | Path                | Description                                                      |
| -------- | ------------------- | ---------------------------------------------------------------- |
| `GET`    | `/`                 | Service banner                                                   |
| `GET`    | `/health`           | Health check                                                     |
| `GET`    | `/users`            | List all users with their notification preferences               |
| `GET`    | `/users/get/:id`    | Fetch a single user, `404` when the id is unknown               |
| `POST`   | `/users/create`     | Create a user, the preference channels must be email/sms/push    |
| `DELETE` | `/users/delete/:id` | Delete a user                                                    |
| `POST`   | `/notify`           | Queue a notification for a recipient                             |
| `GET`    | `/stats`            | Live snapshot of every queue (ready, in flight, consumers, workers) |

### Queueing a notification

```bash
curl -X POST http://127.0.0.1:3000/notify \
  -H "Content-Type: application/json" \
  -d '{
        "priority": "high",
        "recipient_id": "f94e7405-849a-4ee5-aad6-0531cd7912b3",
        "message": { "title": "Hello", "body": "World" }
      }'
```

The delivery channel is resolved from the preference of the recipient for the
given priority, so the example above is routed to the `notifications_email_high`
queue. The endpoint answers with `201` once the broker confirmed the publish,
with `404` when the recipient does not exist and with `400` when the recipient
has no channel configured for that priority.

## Configuration

Every value below can be set in `.env`.

| Variable          | Default   | Description                                                |
| ----------------- | --------- | ---------------------------------------------------------- |
| `PORT`            | `3000`    | Port of the HTTP server                                   |
| `DB_*`            | -         | PostgreSQL connection, the schema is created automatically |
| `RABBITMQ_URL`    | -         | AMQP connection string                                    |
| `PREFETCH_COUNT`  | `10`      | Messages a single worker may hold unacknowledged           |
| `PROCESSING_DELAY`| `0s`      | Artificial delay of the delivery, useful for load tests    |
| `SCALER_INTERVAL` | `7s`      | How often the scheduler rebalances the workers             |

Each queue can be tuned on its own, the key is the channel and the priority in
upper case, for example `EMAIL_HIGH_RPM`, `EMAIL_HIGH_WORKERS` and
`EMAIL_HIGH_MAX_WORKERS`. The rate limit is per queue, the number of workers
decides how many consumers of that queue are started.

## Installation and Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/scalable-notification-service.git
   cd scalable-notification-service
   ```

2. Install dependencies:
   ```bash
   go mod tidy
   ```

3. Configure environment variables:
   - Create a `.env` file based on the `.env.example` provided in the repository.

4. Run the service:
   ```bash
   go run main.go
   ```

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Acknowledgements

- Special thanks to the open-source community for the tools and frameworks used in this project.
