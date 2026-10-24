# FIX-KAFKA.ps1 — Double-click or run from PowerShell in project folder
# Completely resets Kafka and all Docker infrastructure

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  KAFKA + DOCKER FULL RESET" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Stop all containers
Write-Host "[1/5] Stopping all containers..." -ForegroundColor Yellow
docker compose down
Start-Sleep -Seconds 3

# Step 2: Remove ALL kafka volumes by any name variant
Write-Host ""
Write-Host "[2/5] Removing stale Kafka volumes..." -ForegroundColor Yellow
$volumes = docker volume ls -q
foreach ($vol in $volumes) {
    if ($vol -match "kafka") {
        Write-Host "  Removing volume: $vol" -ForegroundColor Red
        docker volume rm $vol --force
    }
}

# Also try explicit names
docker volume rm ecommerce-microservices-main_kafka_data 2>$null
docker volume rm "ecommerce-microservices-main_kafka_data" 2>$null

# Step 3: Prune any dangling volumes
Write-Host ""
Write-Host "[3/5] Pruning dangling volumes..." -ForegroundColor Yellow
docker volume prune -f

# Step 4: Start everything fresh
Write-Host ""
Write-Host "[4/5] Starting all services fresh..." -ForegroundColor Yellow
docker compose up -d
Write-Host "Waiting 30 seconds for Kafka to initialize..." -ForegroundColor Cyan
Start-Sleep -Seconds 30

# Step 5: Status check
Write-Host ""
Write-Host "[5/5] Status check..." -ForegroundColor Yellow
Write-Host ""
Write-Host "--- Running Containers ---" -ForegroundColor Cyan
docker ps --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"

Write-Host ""
Write-Host "--- Kafka Logs (last 20 lines) ---" -ForegroundColor Cyan
docker logs kafka --tail 20

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  DONE!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "If Kafka shows 'started' in logs above:" -ForegroundColor White
Write-Host "  Kafka UI -> http://localhost:8071" -ForegroundColor White
Write-Host ""
Write-Host "Then rebuild and restart your services:" -ForegroundColor White
Write-Host "  .\mvnw clean install -DskipTests" -ForegroundColor White
Write-Host ""
