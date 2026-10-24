package rabbitmq

import (
	"context"
	"fmt"
	"log"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/streadway/amqp"
	"golang.org/x/time/rate"
)

// messagesPerWorker is the backlog a single worker is expected to absorb.
const messagesPerWorker = 10

// WorkerPool describes the workers serving a single queue.
type WorkerPool struct {
	QueueName   string
	RateLimiter *rate.Limiter
	Workers     int
	MaxWorkers  int
	MinWorkers  int

	conn     *amqp.Connection
	prefetch int
	delay    time.Duration

	mu       sync.Mutex
	active   map[int]*workerHandle
	nextID   int
	inFlight int64
}

// workerHandle keeps the ways to stop a running worker.
type workerHandle struct {
	cancel context.CancelFunc
	worker *Worker
	once   sync.Once
}

// stop asks the worker to finish its work and then exit.
func (h *workerHandle) stop() {
	h.once.Do(func() { h.worker.Stop() })
}

// kill aborts the worker right away, the messages it holds are requeued.
func (h *workerHandle) kill() {
	h.once.Do(func() { h.cancel() })
}

func NewWorkerPool(queueName string, rateLimit *rate.Limiter, workers int, maxWorkers int) *WorkerPool {
	return &WorkerPool{
		QueueName:   queueName,
		RateLimiter: rateLimit,
		Workers:     workers,
		MaxWorkers:  maxWorkers,
		MinWorkers:  1,
		active:      make(map[int]*workerHandle),
	}
}

// Setup wires the pool to the broker connection.
func (p *WorkerPool) Setup(conn *amqp.Connection, prefetch int, delay time.Duration) {
	p.conn = conn
	p.prefetch = prefetch
	p.delay = delay
	if p.MinWorkers < 1 {
		p.MinWorkers = 1
	}
	if p.MaxWorkers < p.MinWorkers {
		p.MaxWorkers = p.MinWorkers
	}
}

// InFlight returns the number of messages delivered to this pool that have
// not been settled yet. The broker does not report this number through
// queue.declare-ok, so it is tracked in the process.
func (p *WorkerPool) InFlight() int64 {
	return atomic.LoadInt64(&p.inFlight)
}

// ActiveWorkers returns the number of workers currently running.
func (p *WorkerPool) ActiveWorkers() int {
	p.mu.Lock()
	defer p.mu.Unlock()
	return len(p.active)
}

// StartAll starts the initial workers of the pool.
func (p *WorkerPool) StartAll(ctx context.Context) {
	for i := 0; i < p.Workers; i++ {
		p.startWorker(ctx)
	}
}

// startWorker launches a worker goroutine and registers its cancel function so
// that it can be stopped later on. This is what makes the scaling real, the
// previous implementation only changed a counter.
func (p *WorkerPool) startWorker(ctx context.Context) {
	if p.conn == nil {
		return
	}

	p.mu.Lock()
	p.nextID++
	id := p.nextID
	workerCtx, cancel := context.WithCancel(ctx)
	consumerTag := fmt.Sprintf("%s-worker-%d", p.QueueName, id)
	worker := NewWorker(p.conn, p.QueueName, consumerTag, p.RateLimiter, p.prefetch, p.delay, &p.inFlight)
	p.active[id] = &workerHandle{cancel: cancel, worker: worker}
	p.mu.Unlock()

	go func() {
		defer func() {
			p.mu.Lock()
			delete(p.active, id)
			p.mu.Unlock()
		}()
		worker.Run(workerCtx)
	}()
}

// stopWorkers stops up to n running workers. The workers finish the messages
// they already received before they exit.
func (p *WorkerPool) stopWorkers(n int) {
	p.mu.Lock()
	defer p.mu.Unlock()

	stopped := 0
	for id, handle := range p.active {
		if stopped >= n {
			break
		}
		handle.stop()
		delete(p.active, id)
		stopped++
	}
}

// killWorkers aborts every worker of the pool, the messages they hold are
// returned to the broker.
func (p *WorkerPool) killWorkers() {
	p.mu.Lock()
	defer p.mu.Unlock()

	for id, handle := range p.active {
		handle.kill()
		delete(p.active, id)
	}
}

// scaleTo grows or shrinks the pool so that it runs target workers.
func (p *WorkerPool) scaleTo(ctx context.Context, target int) {
	if target < p.MinWorkers {
		target = p.MinWorkers
	}
	if target > p.MaxWorkers {
		target = p.MaxWorkers
	}

	current := p.ActiveWorkers()
	switch {
	case target > current:
		for i := current; i < target; i++ {
			p.startWorker(ctx)
		}
		log.Printf("Scaled workers for queue %s from %d to %d", p.QueueName, current, target)
	case target < current:
		p.stopWorkers(current - target)
		log.Printf("Scaled workers for queue %s from %d to %d", p.QueueName, current, target)
	}
}

// QueueStats describes the state of a single queue.
type QueueStats struct {
	QueueName string `json:"queue"`
	Ready     int    `json:"ready"`
	InFlight  int64  `json:"in_flight"`
	Consumers int    `json:"consumers"`
	Workers   int    `json:"workers"`
	Min       int    `json:"min_workers"`
	Max       int    `json:"max_workers"`
}

// Depth is the number of messages that still have to be delivered.
func (s QueueStats) Depth() int {
	return s.Ready + int(s.InFlight)
}

// inspector inspects queues on a dedicated channel. A queue that does not
// exist closes the channel on the broker side, so the channel is recreated
// whenever an inspection fails.
type inspector struct {
	conn *amqp.Connection
	ch   *amqp.Channel
}

func (i *inspector) inspect(name string) (amqp.Queue, error) {
	if i.ch == nil {
		ch, err := i.conn.Channel()
		if err != nil {
			return amqp.Queue{}, err
		}
		i.ch = ch
	}

	queue, err := i.ch.QueueInspect(name)
	if err != nil {
		i.ch.Close()
		i.ch = nil
		return amqp.Queue{}, err
	}

	return queue, nil
}

func (i *inspector) close() {
	if i.ch != nil {
		i.ch.Close()
		i.ch = nil
	}
}

var (
	poolsMu sync.RWMutex
	pools   []*WorkerPool

	inspectorMu     sync.Mutex
	activeInspector *inspector
)

// Pools returns the pools registered by the scheduler.
func Pools() []*WorkerPool {
	poolsMu.RLock()
	defer poolsMu.RUnlock()
	return pools
}

// Stats returns a live snapshot of every queue handled by the scheduler.
func Stats() []QueueStats {
	inspectorMu.Lock()
	defer inspectorMu.Unlock()

	if activeInspector == nil {
		return nil
	}

	poolsList := Pools()
	stats := collectStatsLocked(poolsList, activeInspector)

	snapshot := make([]QueueStats, 0, len(poolsList))
	for _, pool := range poolsList {
		snapshot = append(snapshot, stats[pool.QueueName])
	}

	return snapshot
}

// Scheduler starts the workers of every pool and keeps them scaled to the
// backlog of their queue. It blocks until the context is cancelled.
func Scheduler(ctx context.Context, poolsList []*WorkerPool, conn *amqp.Connection, prefetch int, delay time.Duration, interval time.Duration) {
	poolsMu.Lock()
	pools = poolsList
	poolsMu.Unlock()

	for _, pool := range poolsList {
		pool.Setup(conn, prefetch, delay)
		pool.StartAll(ctx)
	}

	inspect := &inspector{conn: conn}
	defer inspect.close()

	inspectorMu.Lock()
	activeInspector = inspect
	inspectorMu.Unlock()

	go scalingLoop(ctx, poolsList, inspect, interval)

	<-ctx.Done()

	for _, pool := range poolsList {
		pool.killWorkers()
	}
}

func scalingLoop(ctx context.Context, poolsList []*WorkerPool, inspect *inspector, interval time.Duration) {
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			stats := collectStats(poolsList, inspect)
			rebalance(ctx, poolsList, stats)
			logStats(poolsList, stats)
		}
	}
}

func collectStats(poolsList []*WorkerPool, inspect *inspector) map[string]QueueStats {
	inspectorMu.Lock()
	defer inspectorMu.Unlock()
	return collectStatsLocked(poolsList, inspect)
}

func collectStatsLocked(poolsList []*WorkerPool, inspect *inspector) map[string]QueueStats {
	stats := make(map[string]QueueStats, len(poolsList))

	for _, pool := range poolsList {
		entry := QueueStats{
			QueueName: pool.QueueName,
			Workers:   pool.ActiveWorkers(),
			Min:       pool.MinWorkers,
			Max:       pool.MaxWorkers,
		}

		queue, err := inspect.inspect(pool.QueueName)
		if err != nil {
			log.Printf("Failed to inspect queue %s: %v", pool.QueueName, err)
		} else {
			entry.Ready = queue.Messages
			entry.Consumers = queue.Consumers
		}

		// Read the in flight counter after the queue so that a message that is
		// acked in between is not counted twice.
		entry.InFlight = pool.InFlight()

		stats[pool.QueueName] = entry
	}

	return stats
}

// rebalance moves the workers of the system to the queues that need them.
func rebalance(ctx context.Context, poolsList []*WorkerPool, stats map[string]QueueStats) {
	budget := 0
	for _, pool := range poolsList {
		budget += pool.MaxWorkers
	}
	if budget <= 0 {
		return
	}

	assigned := make(map[string]int, len(poolsList))
	used := 0
	for _, pool := range poolsList {
		assigned[pool.QueueName] = pool.MinWorkers
		used += pool.MinWorkers
	}

	free := budget - used
	if free < 0 {
		free = 0
	}

	ordered := make([]*WorkerPool, len(poolsList))
	copy(ordered, poolsList)
	sort.SliceStable(ordered, func(i, j int) bool {
		left := stats[ordered[i].QueueName].Ready
		right := stats[ordered[j].QueueName].Ready
		if left != right {
			return left > right
		}
		return getPriorityWeight(ordered[i].QueueName) > getPriorityWeight(ordered[j].QueueName)
	})

	for _, pool := range ordered {
		if free <= 0 {
			break
		}

		// Only the ready messages are a backlog. Messages that are already
		// delivered to a worker are being handled, counting them would make
		// the scaler oscillate between growing and shrinking the pool.
		depth := stats[pool.QueueName].Ready
		if depth <= 0 {
			continue
		}

		want := depth / messagesPerWorker
		if want < 1 {
			want = 1
		}

		room := pool.MaxWorkers - assigned[pool.QueueName]
		if room > free {
			room = free
		}
		if want > room {
			want = room
		}
		if want <= 0 {
			continue
		}

		assigned[pool.QueueName] += want
		free -= want
	}

	for _, pool := range poolsList {
		pool.scaleTo(ctx, assigned[pool.QueueName])
	}
}

func logStats(poolsList []*WorkerPool, stats map[string]QueueStats) {
	total := 0
	ready := 0
	inFlight := 0
	workers := 0

	for _, pool := range poolsList {
		entry := stats[pool.QueueName]
		total += entry.Depth()
		ready += entry.Ready
		inFlight += int(entry.InFlight)
		workers += pool.ActiveWorkers()
	}

	log.Printf("Total messages: %d (ready %d, in flight %d) | workers: %d", total, ready, inFlight, workers)

	for _, pool := range poolsList {
		entry := stats[pool.QueueName]
		if entry.Depth() == 0 {
			continue
		}
		log.Printf("Queue %s has a backlog of %d (ready %d, in flight %d) served by %d workers", entry.QueueName, entry.Depth(), entry.Ready, entry.InFlight, entry.Workers)
	}
}

func getPriorityWeight(queueName string) float64 {
	switch {
	case strings.Contains(queueName, "high"):
		return 1.5
	case strings.Contains(queueName, "medium"):
		return 1.0
	case strings.Contains(queueName, "low"):
		return 0.5
	default:
		return 1.0
	}
}
