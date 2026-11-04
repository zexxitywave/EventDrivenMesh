# Seeds one abandoned cart so you can run the n8n workflow end-to-end.
#
# Prerequisites (running locally, not in Docker):
#   user-service       :8087
#   product-service    :8088
#   inventory-service  :8082
#   cart-service       :8089
#   Redis              :6379
#
# Usage (PowerShell):
#   .\n8n\test-abandoned-cart.ps1 -Email you@example.com
#   .\n8n\test-abandoned-cart.ps1 -Email you@example.com -IdleMinutes 45
#
# Then in n8n: open the imported workflow and click "Execute workflow".

param(
    [string]$UserId = "11111111-1111-1111-1111-111111111111",
    [string]$Email = "you@example.com",
    [string]$FullName = "N8N Demo Customer",
    [int]$IdleMinutes = 45
)

$ErrorActionPreference = "Stop"

$userBase    = "http://localhost:8087"
$productBase = "http://localhost:8088"
$cartBase    = "http://localhost:8089"

function Write-Step($text) {
    Write-Host ""
    Write-Host "==> $text" -ForegroundColor Cyan
}

# ── 1. Profile (used to resolve the customer email) ──────────────────────────
Write-Step "Creating/updating profile $UserId"
$profileBody = @{
    fullName    = $FullName
    email       = $Email
    phoneNumber = "+91-9999999999"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Method Post -Uri "$userBase/api/v1/users/profile" `
        -Headers @{ "X-User-Id" = $UserId } `
        -ContentType "application/json" -Body $profileBody | Out-Null
    Write-Host "Profile created."
}
catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -eq 400) {
        Write-Host "Profile already exists - reusing it."
    }
    else {
        throw
    }
}

# ── 2. Pick a product that exists in the catalog ──────────────────────────────
Write-Step "Fetching an ACTIVE product"
$page = Invoke-RestMethod -Method Get -Uri "$productBase/api/v1/products?size=1&status=ACTIVE"
$product = $page.content | Select-Object -First 1
if (-not $product) {
    throw "No ACTIVE product found. Seed product-service first, then rerun this script."
}
Write-Host ("Using product: {0} ({1})" -f $product.name, $product.id)

# ── 3. Add it to the cart ─────────────────────────────────────────────────────
Write-Step "Adding product to cart"
$addBody = @{
    productId = $product.id
    quantity  = 1
} | ConvertTo-Json

$cart = Invoke-RestMethod -Method Post -Uri "$cartBase/api/v1/cart/items" `
    -Headers @{ "X-User-Id" = $UserId } `
    -ContentType "application/json" -Body $addBody
Write-Host ("Cart now has {0} item(s), total {1}" -f $cart.items.Count, $cart.grandTotal)

# ── 4. Backdate the cart (test-only helper in cart-service) ───────────────────
Write-Step "Simulating $IdleMinutes minutes of inactivity"
Invoke-RestMethod -Method Post `
    -Uri "$cartBase/api/v1/cart/internal/$UserId/simulate-idle?minutes=$IdleMinutes" | Out-Null

# ── 5. Show what n8n will see ─────────────────────────────────────────────────
Write-Step "Abandoned carts (olderThanMinutes=30, stage=NONE)"
$abandoned = Invoke-RestMethod -Method Get `
    -Uri "$cartBase/api/v1/cart/internal/abandoned?olderThanMinutes=30&stage=NONE&limit=10"

Write-Host ("Found {0} abandoned cart(s):" -f $abandoned.count)
$abandoned.carts | ConvertTo-Json -Depth 8

Write-Host ""
Write-Host "Next steps:" -ForegroundColor Green
Write-Host "  1. Start n8n (outside Docker):  npx n8n"
Write-Host "  2. Import: n8n\abandoned-cart-recovery.workflow.json"
Write-Host "  3. For a fast demo, set Config.reminderDelayMinutes = 1 (1 minute)"
Write-Host "  4. Click 'Execute workflow' and watch notification-service send the email"
Write-Host "  5. Re-run this script to create a fresh abandoned cart"
