// Command loadtest sends a burst of notifications to the Scalable Notification
// System so you can observe queueing, rate limiting, worker scaling and the
// scheduler in action.
//
// Usage examples:
//
//	go run ./cmd/loadtest                              // 1 000 msg, 200 concurrent
//	go run ./cmd/loadtest -n 100000 -c 200            // 100 k msg, 200 concurrent
//	go run ./cmd/loadtest -n 100000 -c 500            // push harder
//	go run ./cmd/loadtest -priority medium -channel email -n 50000
//	go run ./cmd/loadtest -user <uuid>                // reuse an existing user
package main

import (
	"bytes"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
)

func main() {
	var (
		baseURL      = flag.String("url", "http://127.0.0.1:3000", "base URL of the running service")
		count        = flag.Int("n", 1000, "number of notifications to send")
		concurrency  = flag.Int("c", 200, "concurrent HTTP senders")
		priority     = flag.String("priority", "high", "notification priority: low, medium, high")
		channel      = flag.String("channel", "push", "delivery channel for the target priority: email, sms, push")
		existingUser = flag.String("user", "", "reuse this user ID instead of creating a test user")
	)
	flag.Parse()

	prefs := map[string]string{"low": "email", "medium": "email", "high": "email"}
	prefs[*priority] = *channel

	recipient := *existingUser
	if recipient == "" {
		recipient = createUser(*baseURL, prefs)
		log.Printf("created test user %s (priority=%s channel=%s)", recipient, *priority, *channel)
	} else {
		log.Printf("using existing user %s", recipient)
	}

	log.Printf("sending %d notifications (priority=%s, concurrency=%d)", *count, *priority, *concurrency)

	// Pre-build the request body once — reused by every goroutine.
	body, _ := json.Marshal(map[string]any{
		"priority":     *priority,
		"recipient_id": recipient,
		"message":      map[string]string{"title": "Load test", "body": "msg"},
	})
	bodyLen := int64(len(body))

	// HTTP client tuned for high concurrency.
	//   MaxIdleConnsPerHost = concurrency  → reuse connections; avoids the TCP
	//   handshake overhead on every request that slowed the old tester down.
	//   ResponseHeaderTimeout              → fail fast so one slow request does
	//   not block a goroutine indefinitely.
	client := &http.Client{
		Transport: &http.Transport{
			MaxIdleConns:          *concurrency + 64,
			MaxIdleConnsPerHost:   *concurrency + 64,
			IdleConnTimeout:       90 * time.Second,
			DisableCompression:    true,
			ForceAttemptHTTP2:     false,
			ResponseHeaderTimeout: 10 * time.Second,
		},
		Timeout: 15 * time.Second,
	}

	var (
		success  int64
		failures int64
		errMap   sync.Map // status_code(string) → count
	)

	// Buffer at 2× concurrency so the feeder is never blocked waiting for a
	// free worker slot — the previous bottleneck with a buffer == concurrency.
	jobs := make(chan struct{}, *concurrency*2)

	var wg sync.WaitGroup
	wg.Add(*concurrency)

	notifyURL := *baseURL + "/notify"
	start := time.Now()

	for w := 0; w < *concurrency; w++ {
		go func() {
			defer wg.Done()
			for range jobs {
				req, _ := http.NewRequest(http.MethodPost, notifyURL, bytes.NewReader(body))
				req.Header.Set("Content-Type", "application/json")
				req.ContentLength = bodyLen

				resp, err := client.Do(req)
				if err != nil {
					atomic.AddInt64(&failures, 1)
					key := "connection_error"
					if v, loaded := errMap.LoadOrStore(key, new(int64)); loaded {
						atomic.AddInt64(v.(*int64), 1)
					}
					continue
				}
				io.Copy(io.Discard, resp.Body)
				resp.Body.Close()

				if resp.StatusCode == http.StatusCreated {
					atomic.AddInt64(&success, 1)
				} else {
					atomic.AddInt64(&failures, 1)
					key := fmt.Sprintf("http_%d", resp.StatusCode)
					if v, loaded := errMap.LoadOrStore(key, new(int64)); loaded {
						atomic.AddInt64(v.(*int64), 1)
					}
				}
			}
		}()
	}

	go func() {
		for i := 0; i < *count; i++ {
			jobs <- struct{}{}
		}
		close(jobs)
	}()

	// Live progress ticker — prints current throughput every second.
	done := make(chan struct{})
	go func() {
		ticker := time.NewTicker(time.Second)
		defer ticker.Stop()
		var lastSuccess int64
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				cur := atomic.LoadInt64(&success)
				elapsed := time.Since(start).Seconds()
				fmt.Printf("\r  progress: %6d / %d  |  instant: %4.0f msg/s  |  avg: %4.0f msg/s    ",
					cur, *count, float64(cur-lastSuccess), float64(cur)/elapsed)
				lastSuccess = cur
			}
		}
	}()

	wg.Wait()
	close(done)
	elapsed := time.Since(start)

	fmt.Printf("\r%80s\r", "") // clear progress line
	fmt.Println()
	fmt.Printf("  Sent:       %d\n", *count)
	fmt.Printf("  Accepted:   %d  (201)\n", atomic.LoadInt64(&success))
	fmt.Printf("  Rejected:   %d\n", atomic.LoadInt64(&failures))

	// Print breakdown of rejection reasons if there were any.
	errMap.Range(func(k, v any) bool {
		fmt.Printf("    %-25s %d\n", k.(string)+":", atomic.LoadInt64(v.(*int64)))
		return true
	})

	fmt.Printf("  Elapsed:    %s\n", elapsed.Round(time.Millisecond))
	fmt.Printf("  Throughput: %.0f msg/s (publish)\n", float64(atomic.LoadInt64(&success))/elapsed.Seconds())
	fmt.Println()
	fmt.Println("Check the live state:")
	fmt.Printf("  curl %s/stats\n", *baseURL)
	fmt.Printf("  open http://localhost:15672  (RabbitMQ management UI, guest/guest)\n")
}

func createUser(baseURL string, prefs map[string]string) string {
	body, _ := json.Marshal(map[string]any{
		"username":   "loadtest_" + uuid.NewString()[:8],
		"email":      "loadtest_" + uuid.NewString()[:8] + "@test.com",
		"phone":      "555" + fmt.Sprintf("%07d", time.Now().UnixNano()%10000000),
		"status":     "active",
		"preference": prefs,
	})

	resp, err := http.Post(baseURL+"/users/create", "application/json", bytes.NewReader(body))
	if err != nil {
		log.Fatalf("failed to create user: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusCreated {
		raw, _ := io.ReadAll(resp.Body)
		log.Fatalf("user creation failed (%d): %s", resp.StatusCode, string(raw))
	}

	var result struct {
		ID string `json:"id"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		log.Fatalf("failed to decode user response: %v", err)
	}
	return result.ID
}
