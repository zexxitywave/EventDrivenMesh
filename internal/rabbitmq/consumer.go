package rabbitmq

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"sync/atomic"
	"time"

	"github.com/SatvikG7/Scalable-Notification-System/internal/models"
	"github.com/streadway/amqp"
	"golang.org/x/time/rate"
)

const (
	// retryHeader carries the number of delivery attempts for a message.
	retryHeader = "x-retry-count"
	// maxRetries is how often a failing message is retried before it is
	// routed to the dead letter queue.
	maxRetries = 3
	// drainTimeout limits how long a worker may take to finish the messages it
	// already received when the pool shrinks.
	drainTimeout = 60 * time.Second
)

// Worker consumes a single queue. Every worker owns its own AMQP channel so
// that a blocked or failed worker can never stall the other workers of the
// same queue.
type Worker struct {
	conn        *amqp.Connection
	queueName   string
	consumerTag string
	rateLimit   *rate.Limiter
	prefetch    int
	delay       time.Duration
	inFlight    *int64

	stop     chan struct{}
	stopOnce sync.Once
}

// NewWorker builds a worker for a queue.
func NewWorker(conn *amqp.Connection, queueName string, consumerTag string, rateLimit *rate.Limiter, prefetch int, delay time.Duration, inFlight *int64) *Worker {
	return &Worker{
		conn:        conn,
		queueName:   queueName,
		consumerTag: consumerTag,
		rateLimit:   rateLimit,
		prefetch:    prefetch,
		delay:       delay,
		inFlight:    inFlight,
		stop:        make(chan struct{}),
	}
}

// Stop asks the worker to finish what it is doing and then exit. The messages
// that were already delivered are processed and acknowledged, so nothing has to
// be requeued when the pool shrinks.
func (w *Worker) Stop() {
	w.stopOnce.Do(func() { close(w.stop) })
}

// Run consumes until the worker is stopped or the channel breaks.
func (w *Worker) Run(ctx context.Context) {
	ch, err := w.conn.Channel()
	if err != nil {
		log.Printf("worker for %s: failed to open channel: %v", w.queueName, err)
		return
	}
	defer ch.Close()

	if err := ch.Qos(w.prefetch, 0, false); err != nil {
		log.Printf("worker for %s: failed to set prefetch: %v", w.queueName, err)
		return
	}

	deliveries, err := ch.Consume(
		w.queueName,   // queue
		w.consumerTag, // consumer
		false,         // auto-ack, set to false for manual ack
		false,         // exclusive
		false,         // no-local
		false,         // no-wait
		nil,           // args
	)
	if err != nil {
		log.Printf("worker for %s: failed to register a consumer: %v", w.queueName, err)
		return
	}

	log.Printf("worker started for %s (prefetch=%d)", w.queueName, w.prefetch)

	for {
		select {
		case <-ctx.Done():
			return
		case <-w.stop:
			w.drain(ch, deliveries)
			return
		case delivery, ok := <-deliveries:
			if !ok {
				log.Printf("worker for %s: delivery channel closed", w.queueName)
				return
			}
			w.handle(ctx, delivery)
		}
	}
}

// drain stops the consumer and processes the deliveries that are still in
// flight, so that the pool can shrink without throwing away work.
func (w *Worker) drain(ch *amqp.Channel, deliveries <-chan amqp.Delivery) {
	if err := ch.Cancel(w.consumerTag, false); err != nil {
		log.Printf("worker for %s: failed to cancel the consumer: %v", w.queueName, err)
	}

	drainCtx, cancel := context.WithTimeout(context.Background(), drainTimeout)
	defer cancel()

	for delivery := range deliveries {
		w.handle(drainCtx, delivery)
	}

	log.Printf("worker for %s stopped", w.queueName)
}

// handle processes a delivery and always settles it, either with an ack or
// with a nack. A delivery that is neither acked nor nacked stays unacked
// forever and, with a prefetch of one, blocks the worker for good.
func (w *Worker) handle(ctx context.Context, delivery amqp.Delivery) {
	if w.inFlight != nil {
		atomic.AddInt64(w.inFlight, 1)
		defer atomic.AddInt64(w.inFlight, -1)
	}

	// Wait blocks until the channel rate limit allows another message, so the
	// message is never dropped on the floor.
	if err := w.rateLimit.Wait(ctx); err != nil {
		// The worker is shutting down, hand the message back to the broker.
		if nackErr := delivery.Nack(false, true); nackErr != nil {
			log.Printf("worker for %s: failed to requeue on shutdown: %v", w.queueName, nackErr)
		}
		return
	}

	if err := w.process(ctx, delivery.Body); err != nil {
		w.handleFailure(ctx, delivery, err)
		return
	}

	if err := delivery.Ack(false); err != nil {
		log.Printf("worker for %s: failed to ack delivery: %v", w.queueName, err)
	}
}

// process simulates the delivery of the notification. The channel was already
// resolved when the notification was accepted, so no database lookup is needed
// here.
func (w *Worker) process(ctx context.Context, body []byte) error {
	var notification models.Notification
	if err := json.Unmarshal(body, &notification); err != nil {
		return fmt.Errorf("decode notification: %w", err)
	}

	if notification.Channel == "" {
		return fmt.Errorf("notification %s has no delivery channel", notification.Id)
	}

	if w.delay > 0 {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(w.delay):
		}
	}

	log.Printf("sent notification %s via %s to %s (queue %s)", notification.Id, notification.Channel, notification.Recipient, w.queueName)

	return nil
}

// handleFailure retries the message and finally routes it to the dead letter
// queue. The original delivery is always settled so the worker never stalls.
func (w *Worker) handleFailure(ctx context.Context, delivery amqp.Delivery, cause error) {
	attempts := retryCount(delivery.Headers)

	if attempts < maxRetries {
		headers := copyHeaders(delivery.Headers)
		headers[retryHeader] = int32(attempts + 1)

		if err := w.republish(w.queueName, delivery.Body, headers); err != nil {
			log.Printf("worker for %s: failed to requeue notification: %v", w.queueName, err)
			if nackErr := delivery.Nack(false, true); nackErr != nil {
				log.Printf("worker for %s: failed to nack delivery: %v", w.queueName, nackErr)
			}
			return
		}

		if ackErr := delivery.Ack(false); ackErr != nil {
			log.Printf("worker for %s: failed to ack retried delivery: %v", w.queueName, ackErr)
		}
		log.Printf("worker for %s: retry %d/%d for notification: %v", w.queueName, attempts+1, maxRetries, cause)
		return
	}

	headers := copyHeaders(delivery.Headers)
	headers["x-error"] = cause.Error()

	if err := w.republish(DeadLetterQueue, delivery.Body, headers); err != nil {
		log.Printf("worker for %s: failed to dead letter notification: %v", w.queueName, err)
		if nackErr := delivery.Nack(false, true); nackErr != nil {
			log.Printf("worker for %s: failed to nack delivery: %v", w.queueName, nackErr)
		}
		return
	}

	if ackErr := delivery.Ack(false); ackErr != nil {
		log.Printf("worker for %s: failed to ack dead lettered delivery: %v", w.queueName, ackErr)
	}
	log.Printf("worker for %s: notification moved to %s after %d attempts: %v", w.queueName, DeadLetterQueue, attempts, cause)
}

func (w *Worker) republish(queueName string, body []byte, headers amqp.Table) error {
	p := CurrentPublisher()
	if p == nil {
		return ErrPublisherNotReady
	}
	return p.PublishWithHeaders(queueName, body, headers)
}

func retryCount(headers amqp.Table) int {
	if headers == nil {
		return 0
	}

	switch value := headers[retryHeader].(type) {
	case int:
		return value
	case int32:
		return int(value)
	case int64:
		return int(value)
	case float64:
		return int(value)
	default:
		return 0
	}
}

func copyHeaders(headers amqp.Table) amqp.Table {
	copied := amqp.Table{}
	for key, value := range headers {
		copied[key] = value
	}
	return copied
}

// NewRateLimiter defines a rate limiter with X requests per minute. The burst
// allows every worker of a pool to hold one token, so scaling the pool out
// actually increases the throughput of the queue.
func NewRateLimiter(requestsPerMinute int, burst int) *rate.Limiter {
	if requestsPerMinute <= 0 {
		requestsPerMinute = 1
	}
	if burst <= 0 {
		burst = 1
	}
	return rate.NewLimiter(rate.Every((time.Minute)/time.Duration(requestsPerMinute)), burst)
}
