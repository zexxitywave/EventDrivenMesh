package rabbitmq

import (
	"log"
	"os"
	"sync"

	"github.com/streadway/amqp"
)

// DeadLetterQueue holds notifications that could not be delivered after the
// configured number of retries.
const DeadLetterQueue = "notifications_dead_letter"

var (
	knownQueuesMu sync.RWMutex
	knownQueues   = map[string]struct{}{}
)

// RegisterQueue marks a queue as declared so that publishes are rejected
// instead of silently creating orphan queues with no consumer attached.
func RegisterQueue(name string) {
	knownQueuesMu.Lock()
	knownQueues[name] = struct{}{}
	knownQueuesMu.Unlock()
}

// IsKnownQueue reports whether the queue was declared by this process.
func IsKnownQueue(name string) bool {
	knownQueuesMu.RLock()
	_, ok := knownQueues[name]
	knownQueuesMu.RUnlock()
	return ok
}

func failOnError(err error, msg string) {
	if err != nil {
		log.Fatalf("%s: %s", msg, err)
	}
}

func ConnectRabbitMQ(pools []*WorkerPool) (*amqp.Connection, *amqp.Channel) {
	conn, err := amqp.Dial(os.Getenv("RABBITMQ_URL"))
	failOnError(err, "Failed to connect to RabbitMQ")

	ch, err := conn.Channel()
	failOnError(err, "Failed to open a channel")

	for _, pool := range pools {
		declareQueue(ch, pool.QueueName)
		RegisterQueue(pool.QueueName)
	}

	declareQueue(ch, DeadLetterQueue)
	RegisterQueue(DeadLetterQueue)

	return conn, ch
}

func declareQueue(ch *amqp.Channel, name string) {
	_, err := ch.QueueDeclare(
		name,  // name
		true,  // durable
		false, // delete when unused
		false, // exclusive
		false, // no-wait
		nil,   // arguments
	)
	failOnError(err, "Failed to declare a queue")
}
