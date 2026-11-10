# Coupon Service

Generates, validates, and redeems discount coupons for the store. Used by the
cart (to lower the order total) and by the n8n abandoned-cart workflow (to send
a real coupon in the second reminder email).

## Run it

The service uses PostgreSQL database `coupon_db` on `localhost:5432`
(`postgres` / `postgres` by default, overridable with `DB_HOST`, `DB_PORT`,
`DB_USER`, `DB_PASSWORD`).

```powershell
# one time, if the database does not exist yet
docker exec postgres psql -U postgres -c "CREATE DATABASE coupon_db;"

# start the service (port 8097)
mvn -pl coupon-service spring-boot:run
```

The table is created automatically by Hibernate (`ddl-auto=update`).

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` / `GET` | `/api/v1/coupons/generate?discountPercent=10&maxUses=5&validityDays=7` | Create a new code |
| `GET` | `/api/v1/coupons/validate?code=CART10-ABCD1234` | Check a code without consuming it |
| `POST` | `/api/v1/coupons/redeem?code=CART10-ABCD1234` | Consume one use |
| `GET` | `/api/v1/coupons/exists?code=CART10-ABCD1234` | Boolean existence check |

`maxUses` and `validityDays` are optional; defaults are 5 uses and 7 days.

### Example

```powershell
$coupon = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8097/api/v1/coupons/generate?discountPercent=10"
$coupon | ConvertTo-Json

Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8097/api/v1/coupons/validate?code=$($coupon.code)"
```

Response:

```json
{
  "id": 1,
  "code": "CART10-K7P2M9QZ",
  "discountPercent": 10,
  "expiresAt": "2026-11-12T10:00:00",
  "usageCount": 0,
  "maxUses": 5,
  "active": true,
  "createdAt": "2026-11-05T10:00:00"
}
```

## Cart integration

cart-service validates the code through this service and stores the discount on
the cart:

```powershell
# apply / remove a coupon
Invoke-RestMethod -Method Post -Uri "http://localhost:8089/api/v1/cart/coupon?code=CART10-K7P2M9QZ" -Headers @{ "X-User-Id" = $userId }
Invoke-RestMethod -Method Delete -Uri "http://localhost:8089/api/v1/cart/coupon" -Headers @{ "X-User-Id" = $userId }
```

The cart response then contains `appliedCouponCode`, `couponDiscountPercent`,
`discountAmount`, and a discounted `grandTotal`. At checkout the coupon is
re-validated (it may have expired or run out of uses), the discount is passed to
order-service, which computes `totalAmount = subtotal - discount + deliveryCharge`,
and `POST /api/v1/coupons/redeem` consumes one use after the order is created.

cart-service points at the coupon service with
`app.coupon.base-url` / the `COUPON_SERVICE_URL` environment variable
(default `http://localhost:8097`).

## n8n integration

The abandoned-cart workflow calls `Generate Coupon` before composing the second
email, so the code in the email is a real, valid coupon from this service. Add
the coupon tunnel URL to the workflow's `Config` node as `couponServiceBaseUrl`
(see `n8n/README.md` §2b).
