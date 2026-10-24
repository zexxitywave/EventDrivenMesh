package rabbitmq

import (
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"github.com/streadway/amqp"
)

const (
	// confirmTimeout is how long a publish may wait for a broker confirmation.
	confirmTimeout = 10 * time.Second

	// poolSize is the number of independent publisher channels kept open.
	// Each channel has its own mutex, so this many publishes can proceed in
	// parallel before any contention is felt.  Match this to roughly your
	// peak HTTP concurrency divided by 4 (broker confirm round-trips are
	// fast, so one channel can serve several goroutines per second).
	poolSize = 32
)

var (
	// ErrPublishNotConfirmed means the broker never acknowledged the publish.
	ErrPublishNotConfirmed = errors.New("publish was not confirmed by the broker")
	// ErrUnknownQueue means a publish targeted a queue that was never declared.
	ErrUnknownQueue = errors.New("unknown queue")
	// ErrPublisherNotReady means the shared publisher has not been initialised.
	ErrPublisherNotReady = errors.New("publisher is not initialised")
)

// Publisher owns a single dedicated AMQP channel with publisher confirms
// enabled.  Calls to Publish are serialised through publishMu so delivery
// tags remain in order on that channel.
type Publisher struct {
	ch       *amqp.Channel
	confirms chan amqp.Confirmation

	publishMu sync.Mutex
	pendingMu sync.Mutex
	nextTag   uint64
	pending   map[uint64]chan amqp.Confirmation
}

// NewPublisher creates a publisher on a dedicated channel of conn.
func NewPublisher(conn *amqp.Connection) (*Publisher, error) {
	ch, err := conn.Channel()
	if err != nil {
		return nil, fmt.Errorf("open publisher channel: %w", err)
	}

	if err := ch.Confirm(false); err != nil {
		ch.Close()
		return nil, fmt.Errorf("enable publisher confirms: %w", err)
	}

	p := &Publisher{
		ch:       ch,
		confirms: ch.NotifyPublish(make(chan amqp.Confirmation, 1024)),
		pending:  make(map[uint64]chan amqp.Confirmation),
	}

	go p.dispatchConfirms()

	return p, nil
}

func (p *Publisher) dispatchConfirms() {
	for confirmation := range p.confirms {
		p.pendingMu.Lock()
		waiter, ok := p.pending[confirmation.DeliveryTag]
		if ok {
			delete(p.pending, confirmation.DeliveryTag)
		}
		p.pendingMu.Unlock()

		if ok {
			waiter <- confirmation
		}
	}
}

// Publish sends a message to the queue and waits for the broker confirmation.
func (p *Publisher) Publish(queueName string, body []byte) error {
	return p.publish(queueName, body, nil)
}

// PublishWithHeaders sends a message with extra AMQP headers (used for retries).
func (p *Publisher) PublishWithHeaders(queueName string, body []byte, headers amqp.Table) error {
	return p.publish(queueName, body, headers)
}

func (p *Publisher) publish(queueName string, body []byte, headers amqp.Table) error {
	if !IsKnownQueue(queueName) {
		return fmt.Errorf("%w: %s", ErrUnknownQueue, queueName)
	}

	waiter := make(chan amqp.Confirmation, 1)

	p.publishMu.Lock()
	p.nextTag++
	tag := p.nextTag
	p.pendingMu.Lock()
	p.pending[tag] = waiter
	p.pendingMu.Unlock()

	err := p.ch.Publish(
		"",
		queueName,
		false,
		false,
		amqp.Publishing{
			ContentType:  "application/json",
			DeliveryMode: amqp.Persistent,
			MessageId:    fmt.Sprintf("%d", tag),
			Headers:      headers,
			Body:         body,
		},
	)
	p.publishMu.Unlock()

	if err != nil {
		p.removePending(tag)
		return fmt.Errorf("publish to %s: %w", queueName, err)
	}

	select {
	case confirmation := <-waiter:
		if !confirmation.Ack {
			return fmt.Errorf("%w: %s", ErrPublishNotConfirmed, queueName)
		}
		return nil
	case <-time.After(confirmTimeout):
		p.removePending(tag)
		return fmt.Errorf("%w: %s", ErrPublishNotConfirmed, queueName)
	}
}

func (p *Publisher) removePending(tag uint64) {
	p.pendingMu.Lock()
	delete(p.pending, tag)
	p.pendingMu.Unlock()
}

// Close releases the AMQP channel.
func (p *Publisher) Close() error {
	return p.ch.Close()
}

// ---------------------------------------------------------------------------
// Publisher pool — keeps poolSize independent Publisher instances so that
// concurrent HTTP handlers can publish in parallel without all serialising
// through a single mutex.
// ---------------------------------------------------------------------------

// publisherPool holds the pool of Publishers and a round-robin counter.
type publisherPool struct {
	publishers []*Publisher
	size       int64
	counter    atomic.Int64
}

func (pool *publisherPool) get() *Publisher {
	idx := pool.counter.Add(1) % pool.size
	return pool.publishers[idx]
}

func (pool *publisherPool) close() {
	for _, p := range pool.publishers {
		p.Close()
	}
}

var (
	globalPoolMu sync.RWMutex
	globalPool   *publisherPool
)

// InitPublisher opens poolSize AMQP channels and registers them as the
// process-wide publisher pool.
func InitPublisher(conn *amqp.Connection) error {
	publishers := make([]*Publisher, poolSize)
	for i := range publishers {
		p, err := NewPublisher(conn)
		if err != nil {
			// Close any already-opened publishers before returning.
			for j := 0; j < i; j++ {
				publishers[j].Close()
			}
			return fmt.Errorf("init publisher %d/%d: %w", i+1, poolSize, err)
		}
		publishers[i] = p
	}

	pool := &publisherPool{publishers: publishers, size: int64(poolSize)}

	globalPoolMu.Lock()
	globalPool = pool
	globalPoolMu.Unlock()

	return nil
}

// CurrentPublisher returns any single publisher from the pool (used by the
// consumer retry/dead-letter path which doesn't need high concurrency).
func CurrentPublisher() *Publisher {
	globalPoolMu.RLock()
	defer globalPoolMu.RUnlock()
	if globalPool == nil {
		return nil
	}
	return globalPool.get()
}

// ClosePublisher shuts down the whole publisher pool.
func ClosePublisher() {
	globalPoolMu.Lock()
	if globalPool != nil {
		globalPool.close()
		globalPool = nil
	}
	globalPoolMu.Unlock()
}

// PublishNotification picks a publisher from the pool and publishes the
// notification.  Concurrent callers get different publishers, so they run
// truly in parallel with no single-mutex bottleneck.
func PublishNotification(queueName string, body []byte) error {
	globalPoolMu.RLock()
	pool := globalPool
	globalPoolMu.RUnlock()

	if pool == nil {
		return ErrPublisherNotReady
	}
	return pool.get().Publish(queueName, body)
}
