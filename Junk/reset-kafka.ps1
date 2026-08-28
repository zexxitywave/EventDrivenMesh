# reset-kafka.ps1 — Run this from the project root folder
# Completely wipes Kafka state and restarts everything clean

Write-Host "==> Stopping all containers..." -ForegroundColor Yellow
docker compose down

Write-Host "==> Force removing kafka_data volume..." -ForegroundColor Yellow
docker volume rm ecommerce-microservices-main_kafka_data 2>$null
docker volume rm ecommerce-microservices_kafka_data 2>$null
# Try all common name variants
docker volume ls --filter name=kafka_data -q | ForEach-Object { docker volume rm $_ }

Write-Host "==> Starting all services..." -ForegroundColor Yellow
docker compose up -d

Write-Host ""
Write-Host "==> Waiting 20s for Kafka to fully initialize..." -ForegroundColor Cyan
Start-Sleep -Seconds 20

Write-Host ""
Write-Host "==> Container status:" -ForegroundColor Cyan
docker ps --format "table {{.Names}}`t{{.Status}}"

Write-Host ""
Write-Host "==> Kafka logs (last 20 lines):" -ForegroundColor Cyan
docker logs kafka --tail 20

Write-Host ""
Write-Host "Done! Now restart your Spring Boot services." -ForegroundColor Green
