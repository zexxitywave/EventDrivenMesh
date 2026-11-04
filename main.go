package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/SatvikG7/Scalable-Notification-System/config"
	"github.com/SatvikG7/Scalable-Notification-System/internal/db"
	"github.com/SatvikG7/Scalable-Notification-System/internal/rabbitmq"
	"github.com/SatvikG7/Scalable-Notification-System/internal/server"
)

type poolDefaults struct {
	channel  string
	priority string
	rpm      int
	workers  int
	max      int
}

func main() {
	if err := config.ConfigENV(); err != nil {
		fmt.Println("Error loading environment variables")
		return
	}

	if err := db.Init(); err != nil {
		fmt.Println("Error initializing database")
		return
	}

	pools := buildPools()

	conn, ch := rabbitmq.ConnectRabbitMQ(pools)
	defer conn.Close()
	defer ch.Close()

	if err := rabbitmq.InitPublisher(conn); err != nil {
		log.Fatalf("Failed to initialise the publisher: %v", err)
	}
	defer rabbitmq.ClosePublisher()

	go func() {
		if err := server.Init(); err != nil {
			log.Fatalf("Server error: %v", err)
		}
	}()

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-signals
		log.Println("Shutting down")
		cancel()
	}()

	prefetch := config.EnvInt("PREFETCH_COUNT", 10)
	delay := config.EnvDuration("PROCESSING_DELAY", 0)
	interval := config.EnvDuration("SCALER_INTERVAL", 7*time.Second)

	rabbitmq.Scheduler(ctx, pools, conn, prefetch, delay, interval)
}

// buildPools creates one worker pool per channel and priority. Every value
// can be overridden through the environment, for example EMAIL_HIGH_RPM,
// EMAIL_HIGH_WORKERS and EMAIL_HIGH_MAX_WORKERS.
func buildPools() []*rabbitmq.WorkerPool {
	defaults := []poolDefaults{
		{"email", "high", 50, 3, 5},
		{"sms", "high", 10, 3, 5},
		{"push", "high", 100, 3, 5},
		{"email", "medium", 25, 2, 3},
		{"sms", "medium", 6, 2, 3},
		{"push", "medium", 75, 2, 3},
		{"email", "low", 10, 1, 2},
		{"sms", "low", 3, 1, 2},
		{"push", "low", 50, 1, 2},
	}

	pools := make([]*rabbitmq.WorkerPool, 0, len(defaults))

	for _, def := range defaults {
		key := strings.ToUpper(def.channel) + "_" + strings.ToUpper(def.priority)

		rpm := config.EnvInt(key+"_RPM", def.rpm)
		workers := config.EnvInt(key+"_WORKERS", def.workers)
		maxWorkers := config.EnvInt(key+"_MAX_WORKERS", def.max)

		queue := fmt.Sprintf("notifications_%s_%s", def.channel, def.priority)
		pools = append(pools, rabbitmq.NewWorkerPool(queue, rabbitmq.NewRateLimiter(rpm, workers), workers, maxWorkers))
	}

	return pools
}
