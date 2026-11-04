# n8n Abandoned Cart Recovery

This folder adds **n8n orchestration** on top of the existing Spring Boot mesh. It does not
replace any service, and **n8n runs outside Docker** — via `npx`, a global npm install, or the
n8n desktop app.

The flow implemented here:

```
Schedule Trigger (every 5 min)
        |
        v
cart-service: GET /internal/abandoned   -> carts idle 30+ min, reminderStage = NONE
        |
        v
Split Carts                             -> one n8n item per cart
        |
        v
user-service: GET /internal/{userId}     -> customer email + email preference
        |
        v
order-service: GET /customer/{id}/purchased?productIds=...&since=...
        |
        v
IF "Should Remind?"  ── purchased / opted out ──> Stop
        |
        v
notification-service: POST /internal/send   (email #1)
        |
        v
cart-service: PATCH /{userId}/reminder-state { stage: REMINDED }
        |
        v
Wait 24h
        |
        v
order-service: purchase check again
        |
        v
IF purchased? ── yes ──> Stop
        |
        no
        v
build coupon -> notification-service (email #2)
        |
        v
cart-service: PATCH /{userId}/reminder-state { stage: COUPON_SENT }
```

**Division of responsibility:** n8n owns scheduling, waiting, branching and retries.
Spring Boot still owns carts, orders, customers and email delivery. Workflow state
(`reminderStage`) lives in cart-service so a scheduled run can never email the same
customer twice.

---

## 1. What was added to the services

| Service | Endpoint | Purpose |
|---|---|---|
| cart-service (8089) | `GET /api/v1/cart/internal/abandoned?olderThanMinutes=30&stage=NONE&limit=100` | Returns `{ count, carts: [...] }` for carts with items that have been idle, optionally filtered by reminder stage |
| cart-service (8089) | `PATCH /api/v1/cart/internal/{userId}/reminder-state` | Set `stage` to `NONE`, `REMINDED` or `COUPON_SENT` |
| cart-service (8089) | `POST /api/v1/cart/internal/{userId}/simulate-idle?minutes=45` | **Test only** — backdates a cart so you don't wait 30 minutes |
| user-service (8087) | `GET /api/v1/users/internal/{userId}` | Returns `{ userId, fullName, email, emailNotifications }` or 404 |
| order-service (8081) | `GET /api/v1/orders/customer/{id}/purchased?productIds=a,b&since=ISO` | `{ purchased, matchedOrderId, ... }`, reading the write model |
| notification-service (8084) | `POST /api/v1/notifications/internal/send` | Sends a one-off email and stores the notification row |

`cart-service` also resets `reminderStage` to `NONE` whenever the customer genuinely
changes their cart, and cart checkout deletes the cart as before.

> These `/internal/**` endpoints bypass the API Gateway (the gateway does not route them).
> In production, restrict them to your automation network or require a shared secret header.

---

## 2. Run n8n without Docker

> Already using n8n Cloud (`https://<instance>.app.n8n.cloud`)? Skip to
> [§2b Using n8n Cloud](#2b-using-n8n-cloud-hosted) — `localhost` URLs will not work there.

### Option A — no install (recommended for trying it)

```powershell
npx n8n
```

The first run downloads n8n. When it finishes, open <http://localhost:5678>.

### Option B — global install

```powershell
npm install -g n8n
n8n
```

### Option C — n8n Desktop app

Install the desktop app from <https://n8n.io/download>, open it, and use the same UI.

### Optional environment variables (Windows PowerShell)

```powershell
$env:N8N_PORT="5678"
$env:N8N_SECURE_COOKIE="false"       # allows http://localhost without a secure cookie
$env:GENERIC_TIMEZONE="Asia/Kolkata"
$env:TZ="Asia/Kolkata"
npx n8n
```

n8n stores its SQLite database and workflow executions in `%USERPROFILE%\.n8n`.
Keep that folder if you want your workflows to survive restarts.

**Node version:** use Node.js 20 LTS or newer (check with `node -v`). n8n will warn if
the version is too old.

> If you use **n8n Cloud** instead of running locally, `localhost` points at the cloud
> machine, not your laptop. See §2b below.

---

## 2b. Using n8n Cloud (hosted)

If n8n runs at `https://<instance>.app.n8n.cloud`, the base URLs in the `Config` node must
be reachable **from the internet**. `http://localhost:8089` means the n8n Cloud server's
localhost, so every HTTP node will fail.

### Fast demo: Cloudflare Quick Tunnels

```powershell
# one time
winget install --id Cloudflare.cloudflared

# open a new terminal, then:
.\n8n\start-tunnels.ps1
```

The script starts one outbound-only tunnel per service and prints four
`https://<random>.trycloudflare.com` URLs. Paste them into the `Config` node:

| Config field | Tunnel target |
|---|---|
| `cartServiceBaseUrl` | `http://localhost:8089` |
| `orderServiceBaseUrl` | `http://localhost:8081` |
| `userServiceBaseUrl` | `http://localhost:8087` |
| `notificationServiceBaseUrl` | `http://localhost:8084` |

Stop them when you are done:

```powershell
.\n8n\stop-tunnels.ps1
```

### Things that bite on n8n Cloud

- **Quick-tunnel URLs are ephemeral.** They change every time you restart the tunnels.
  Re-run the script and update `Config` before executing the workflow.
- **No inbound firewall rules are needed.** `cloudflared` makes outbound connections only;
  you do not need to open ports 8081/8084/8087/8089 on your router.
- **Tunnels expose unauthenticated endpoints.** While the tunnels are up, anyone with the
  URL can call `/internal/**`. Keep them short-lived for the demo, and add a shared-secret
  header before exposing them for real.
- **Execution budget.** Every workflow run counts as an execution. A 5-minute schedule is
  ~288 executions/day, which will burn through a 1,000-execution trial quickly. For the
  demo, keep the workflow **inactive** and click **Execute workflow** manually. If you
  activate it, use an hourly schedule.
- **Long waits.** Set `Config.reminderDelayMinutes` to `1` or `2` for the Cloud demo. A
  24-hour `Wait` works on many n8n Cloud plans, but long-running executions are subject to
  your plan's limits — verify before relying on `1440`.
- **The test script still runs locally.** Start the services on your machine and seed the
  cart with `.\n8n\test-abandoned-cart.ps1`; only n8n lives in the cloud.

### Stable alternative

For a permanent setup, either:

- run the Spring services on a public host (VPS, Render, Railway, Fly.io) and change
  `Config` to those HTTPS URLs, or
- use a **named** Cloudflare Tunnel with subdomains such as
  `cart.zexxity.online`, `orders.zexxity.online`, `users.zexxity.online`,
  `notifications.zexxity.online` instead of four ephemeral quick tunnels.

---

## 3. Import and configure the workflow

1. In n8n: **Workflows → Create Workflow → ⋯ → Import from File**.
2. Select `n8n/abandoned-cart-recovery.workflow.json`.
3. Open the **Config** node and check the URLs/ports:

| Field | Default | Meaning |
|---|---|---|
| `cartServiceBaseUrl` | `http://localhost:8089` | cart-service |
| `orderServiceBaseUrl` | `http://localhost:8081` | order-service |
| `userServiceBaseUrl` | `http://localhost:8087` | user-service |
| `notificationServiceBaseUrl` | `http://localhost:8084` | notification-service |
| `olderThanMinutes` | `30` | how long a cart must be idle |
| `limit` | `100` | max carts per run |
| `reminderDelayMinutes` | `1440` | wait between email #1 and the coupon (24h) |
| `couponDiscountPercent` | `10` | discount shown in email #2 |
| `fallbackEmail` | empty | used when the customer has no profile/email |

4. Save the workflow. It is imported **inactive** so nothing runs until you say so.

---

## 4. Run it end-to-end

### Prerequisites

Start the infrastructure (`docker compose up -d kafka mongodb redis postgres` — this is
your existing project infra; n8n itself is **not** in Docker) and these services from your
IDE or `mvn spring-boot:run`:

- service-registry (Eureka) `:8761` — cart-service resolves `product-service`,
  `inventory-service` and `order-service` by name through Eureka, so add-to-cart fails
  without it
- user-service `:8087`
- product-service `:8088`
- inventory-service `:8082`
- cart-service `:8089`
- order-service `:8081` (needed only for the purchase check)
- notification-service `:8084`

> If you only run the n8n workflow (the cart is already seeded in Redis), Eureka is not
> needed — all n8n calls go directly to `localhost:<port>`. Eureka is required by the
> **seed script**, because `POST /api/v1/cart/items` calls product/inventory by service name.

Email delivery uses the notification-service's existing SMTP config
(`MAIL_PASSWORD` / `spring.mail.*`). If email is not configured, the workflow still runs, but
the notification row ends up `FAILED`/`RETRY_PENDING` in MongoDB.

### Seed an abandoned cart

```powershell
.\n8n\test-abandoned-cart.ps1 -Email you@example.com
```

This creates a profile, picks an ACTIVE product, adds it to the cart, backdates the cart by
45 minutes, and prints the abandoned-cart payload n8n will receive.

### Execute

For a **fast demo**, set `Config.reminderDelayMinutes = 1` (one minute), then click
**Execute workflow** in n8n.

For a **real 24h run**, leave it at `1440`, save, and **activate** the workflow. The schedule
trigger will pick up new abandoned carts every 5 minutes. Executions paused in the Wait node
resume automatically as long as n8n is running; n8n persists queued executions, so a short
restart is safe.

### Expected result

| Step | What you should see |
|---|---|
| Email #1 | `ABANDONED_CART_REMINDER` notification in `notification_db.notifications`, status `SENT` |
| cart-service | `reminderStage = REMINDED` for the cart |
| Next schedule run | the same cart is **not** returned by `?stage=NONE`, so no duplicate email |
| After the wait | if no order was placed, email #2 (`ABANDONED_CART_COUPON`) is sent and stage becomes `COUPON_SENT` |
| If you checkout | the purchase check returns `purchased: true` and the flow stops |

To test the "customer purchased" branch, place an order for the same product and customer
after seeding the cart but before executing the workflow. Re-running the seed script resets
the cart stage to `NONE`, so you can repeat the demo.

---

## 5. Learning notes

- **HTTP Request nodes** are the only integration points — no n8n database access. That keeps
  the domain logic in the services that own it.
- **Split Out** turns the abandoned-cart array into one item, so every later node processes
  each cart independently (n8n's item-based execution model).
- **Code nodes** only format data (merge cart + customer, compose email HTML). They do not
  contain business rules.
- **IF nodes** implement the branch from the diagram: purchase check, then optional coupon.
- **Wait** is why n8n is useful here: the execution can pause for 24 hours without a cron
  job, database column, or custom scheduler in your services.
- **Prepare Wait / Attach Cart After Wait** are small Code nodes that snapshot the cart before
  the pause and re-attach it afterwards. Item pairing across a `Wait` is fragile, so the
  resumed branch reads `$json` from its own snapshot instead of reaching back to an earlier node.
- **Idempotency** comes from `reminderStage` in cart-service, not from n8n. If you later add
  retries around "Send Reminder Email", move the stage update before the send or add a
  unique outbox key.

---

## 6. Production checklist

- Protect `/internal/**` endpoints (network policy, mTLS, or a shared-secret header checked
  by a small filter).
- Replace the Redis `KEYS cart:*` scan with a sorted set or an indexed table.
- Add a real coupon service; the coupon code in this demo is generated in n8n and is not
  redeemable by checkout.
- Cap workflow concurrency and add per-customer rate limits.
- Reconcile carts stuck in `REMINDED`: if an n8n execution is lost during the 24h wait, the
  cart is never picked up again by `?stage=NONE`. A daily job can re-drive carts whose
  `lastReminderAt` is older than, say, 48h.
- Use n8n credentials/environment variables for service URLs and secrets instead of the
  plain `Config` node.
- If you want event-driven instead of polling, add a cart-service webhook or a Kafka trigger
  (community node) that starts this workflow when a cart becomes idle.
