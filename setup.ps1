# Scalable Notification System Setup Script
# Run this PowerShell script to check the environment and prepare the database.

$ErrorActionPreference = "Stop"

Write-Host "=== Scalable Notification System Setup ===" -ForegroundColor Cyan

# Step 1: locate psql
Write-Host "Step 1: Locating PostgreSQL client..."

$psql = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName

if (-not $psql) {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($psqlCmd) { $psql = $psqlCmd.Source }
}

if (-not $psql) {
    Write-Host "  psql.exe was not found. Install PostgreSQL or add it to PATH." -ForegroundColor Red
    exit 1
}

Write-Host "  Using $psql" -ForegroundColor Green

# Step 2: read the connection settings from .env
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "  .env not found, copy .env.example to .env first." -ForegroundColor Red
    exit 1
}

$settings = @{}
Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z0-9_]+)\s*=\s*(.*)\s*$') {
        $settings[$Matches[1]] = $Matches[2].Trim()
    }
}

$dbHost    = if ($settings["DB_HOST"]) { $settings["DB_HOST"] } else { "localhost" }
$dbPort    = if ($settings["DB_PORT"]) { $settings["DB_PORT"] } else { "5432" }
$dbUser    = if ($settings["DB_USER"]) { $settings["DB_USER"] } else { "postgres" }
$dbName    = if ($settings["DB_NAME"]) { $settings["DB_NAME"] } else { "postgres" }
$dbPass    = if ($settings["DB_PASSWORD"]) { $settings["DB_PASSWORD"] } else { "" }
$rabbitUrl = if ($settings["RABBITMQ_URL"]) { $settings["RABBITMQ_URL"] } else { "amqp://guest:guest@localhost:5672/" }

$env:PGPASSWORD = $dbPass

# Step 3: check the database
Write-Host "Step 2: Checking database '$dbName' on $dbHost`:$dbPort ..."

$check = & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -tAc "SELECT 1;" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Cannot connect to the database:" -ForegroundColor Red
    Write-Host "  $check" -ForegroundColor Red
    exit 1
}
Write-Host "  Connection OK" -ForegroundColor Green

# Step 4: create the schema
Write-Host "Step 3: Applying schema.sql ..."

$schema = Join-Path $PSScriptRoot "schema.sql"
$apply = & $psql -h $dbHost -p $dbPort -U $dbUser -d $dbName -v ON_ERROR_STOP=1 -f $schema 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Failed to apply the schema:" -ForegroundColor Red
    Write-Host "  $apply" -ForegroundColor Red
    exit 1
}
Write-Host "  Schema applied" -ForegroundColor Green

# Step 5: check RabbitMQ
Write-Host "Step 4: Checking RabbitMQ at $rabbitUrl ..."
$parsed = [System.Uri]$rabbitUrl
$rabbitPort = $parsed.Port
$tcp = Test-NetConnection -ComputerName $parsed.Host -Port $rabbitPort -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "  RabbitMQ is not reachable on port $rabbitPort." -ForegroundColor Red
    exit 1
}
Write-Host "  RabbitMQ is reachable" -ForegroundColor Green

Write-Host "=== Setup Complete ===" -ForegroundColor Cyan
Write-Host "1. Keep this terminal open"
Write-Host "2. Run: go run main.go"
Write-Host "3. Visit: http://127.0.0.1:3000"
Write-Host "4. Test API endpoints as described in README.md"
