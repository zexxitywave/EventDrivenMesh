<#
.SYNOPSIS
    HTTP-level load test: CQRS read-model endpoint vs write-table endpoint.

.DESCRIPTION
    Compares average latency of
      GET  /api/v1/orders/{orderId}        -> served from order_reads (READ MODEL)
      GET  /api/v1/orders/write/{orderId}  -> served from orders        (WRITE TABLE)
    with no traffic, then again while several writer jobs POST new orders
    concurrently. Demonstrates that read-model reads stay fast / isolated while
    the write table absorbs the POST load.

    Requires order-service running (direct port, no gateway/auth). Writers use
    curl.exe with a JSON body file (PowerShell mangles raw double-quoted JSON
    when passed as a native -d argument).

.EXAMPLE
    .\scripts\http-loadtest.ps1
.EXAMPLE
    .\scripts\http-loadtest.ps1 -Base http://localhost:8081/api/v1/orders -WriterProcesses 5 -OrdersPerWriter 30 -ReadSamples 100
#>
[CmdletBinding()]
param(
    [string]$Base             = "http://localhost:8081/api/v1/orders",
    [string]$CustomerId       = "2cc7f367-05dd-465d-98ad-2841fb8c2b01",
    [string]$CustomerEmail    = "creditskumar@gmail.com",
    [string]$ProductId        = "4f9334f0-3fb3-45e7-acc4-f1d3183986cd",
    [string]$ProductName      = "Smartphone 12",
    [int]$WriterProcesses     = 3,
    [int]$OrdersPerWriter     = 20,
    [int]$ReadSamples         = 60,
    [int]$WarmupSeconds       = 3
)

$ErrorActionPreference = "Stop"

$curlExe = (Get-Command curl.exe -ErrorAction Stop).Source

function Invoke-Json([string]$Method, [string]$Url, [string]$Body = $null) {
    $cmdArgs = @("-s", "-X", $Method, "-H", "Content-Type: application/json")
    if ($Body) { $cmdArgs += @("-d", $Body) }
    $raw = & $curlExe @cmdArgs $Url
    return ($raw | Out-String)
}

function Get-AvgLatencyMs([string]$Url, [int]$Samples) {
    $sum = 0.0
    for ($i = 0; $i -lt $Samples; $i++) {
        $t = (& $curlExe -s -o NUL -w "%{time_total}" $Url | Out-String).Trim()
        $sec = 0.0
        try { $sec = [double]$t } catch { }
        $sum += $sec
    }
    return [Math]::Round(($sum / [Math]::Max(1, $Samples)) * 1000, 1)
}

function Write-Header($t) { Write-Host ""; Write-Host ("==== $t ====") -ForegroundColor Cyan }

# ── 1. find one real order id ─────────────────────────────────────────────
Write-Header "Locating an order for the demo user"
$listJson = Invoke-Json "GET" "$Base/customer/$CustomerId"
$orders = $listJson | ConvertFrom-Json
if (-not $orders -or $orders.Count -eq 0) { throw "No orders found for customer $CustomerId" }
$orderId = $orders[0].orderId
Write-Host ("using orderId = {0} (read-model status={1}, source={2})" -f $orderId, $orders[0].status, $orders[0].source) -ForegroundColor Green

$readUrl  = "$Base/$orderId"
$writeUrl = "$Base/write/$orderId"

# ── 2. baseline (no writers) ──────────────────────────────────────────────
Write-Header "Phase 1: baseline latency (no POST traffic)"
$baseRead  = Get-AvgLatencyMs $readUrl  $ReadSamples
$baseWrite = Get-AvgLatencyMs $writeUrl $ReadSamples
Write-Host ("  READ_MODEL  GET /orders/{{id}}      : {0,8:N1} ms  (x{1:N2} of write)" -f $baseRead, ($baseWrite / $baseRead))
Write-Host ("  WRITE Table GET /orders/write/{{id}}: {0,8:N1} ms" -f $baseWrite) -ForegroundColor Yellow

# ── 3. mixed load ─────────────────────────────────────────────────────────
Write-Header ("Phase 2: mixed load ({0} writers x {1} orders each)" -f $WriterProcesses, $OrdersPerWriter)
$json = '{"customerId":"' + $CustomerId + '","customerEmail":"' + $CustomerEmail +
        '","items":[{"productId":"' + $ProductId + '","productName":"' + $ProductName +
        '","quantity":1,"price":0.10}]}'

$bodyFile = Join-Path $env:TEMP "edm-order-body.json"
Set-Content -LiteralPath $bodyFile -Value $json -NoNewline

$beforeCount = ((Invoke-Json "GET" "$Base/customer/$CustomerId") | ConvertFrom-Json).Count

$writerJob = {
    param($exe, $url, $file, $n)
    for ($i = 0; $i -lt $n; $i++) {
        & $exe -s -o NUL -X POST -H "Content-Type: application/json" --data-binary "@$file" $url
    }
}
$jobs = @()
for ($w = 0; $w -lt $WriterProcesses; $w++) {
    $jobs += Start-Job -ScriptBlock $writerJob -ArgumentList $curlExe, $Base, $bodyFile, $OrdersPerWriter
}
Start-Sleep -Seconds $WarmupSeconds

$underRead  = Get-AvgLatencyMs $readUrl  $ReadSamples
$underWrite = Get-AvgLatencyMs $writeUrl $ReadSamples

$jobs | Wait-Job -Timeout 300 | Out-Null
$jobs | Receive-Job | Out-Null
Remove-Job -Force $jobs

# Poll the READ model until it catches up — the projection is eventually
# consistent, so this shows the lag in real time.
$expected = $WriterProcesses * $OrdersPerWriter
Write-Host ("Polls of read-model customer count (eventual consistency): {0} -> ?" -f $beforeCount) -ForegroundColor Gray
$afterCount = $beforeCount
for ($i = 1; $i -le 12; $i++) {
    Start-Sleep -Seconds 3
    $c = ((Invoke-Json "GET" "$Base/customer/$CustomerId") | ConvertFrom-Json).Count
    Write-Host ("  poll {0,2}: {1}" -f $i, $c) -ForegroundColor Gray
    if ($c -eq $afterCount) { $afterCount = $c; break }
    $afterCount = $c
}
$created = $afterCount - $beforeCount
if ($created -lt $expected) {
    Write-Host ("WARNING: only {0} of {1} POSTs visible in read model yet - check order-service" -f $created, $expected) -ForegroundColor Magenta
} else {
    Write-Host ("verified: {0} new orders created and projected ({1} -> {2})" -f $created, $beforeCount, $afterCount) -ForegroundColor Green
}

# ── 4. report ─────────────────────────────────────────────────────────────
Write-Header "Results"
Write-Host ("  ENDPOINT                      BASELINE   UNDER LOAD   SLOWDOWN")
Write-Host ("  READ_MODEL  GET /orders/{{id}}   {0,8:N1}ms  {1,8:N1}ms   {2,6:N1}x" -f $baseRead, $underRead, ($underRead / $baseRead)) -ForegroundColor Green
Write-Host ("  WRITE Table GET /orders/write  {0,8:N1}ms  {1,8:N1}ms   {2,6:N1}x" -f $baseWrite, $underWrite, ($underWrite / $baseWrite)) -ForegroundColor Yellow

Write-Host ""
Write-Host "Notes: new orders stay at INVENTORY_CHECKING because inventory/payment/" -ForegroundColor Gray
Write-Host "shipping services are not running - that does not affect the read-vs-write" -ForegroundColor Gray
Write-Host "latency comparison (both models see the identical saga state)." -ForegroundColor Gray