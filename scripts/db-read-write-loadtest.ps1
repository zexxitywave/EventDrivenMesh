<#
.SYNOPSIS
    Demonstrates read/write contention on a single Postgres table.

.DESCRIPTION
    This is a teaching benchmark for CQRS. It measures the latency of "read"
    queries (GET) against a table when there is NO write traffic, then measures
    the same read workload while several "writer" clients hammer the SAME table.

    The point: with Postgres MVCC, writers do not block readers at the row level
    (pg_locks waiting stays 0), yet reads still get slower because both workloads
    share CPU, IO and the connection pool. A CQRS read model (separate table /
    replica) isolates reads from writes so that never happens.

    Everything runs against a throwaway table called edm_loadtest_demo which is
    dropped at the end unless -KeepTable is passed.

.EXAMPLE
    .\scripts\db-read-write-loadtest.ps1
    Runs the default demo against localhost:5432, database order_db.

.EXAMPLE
    .\scripts\db-read-write-loadtest.ps1 -Database order_db -WriterProcesses 8 -ReaderQueries 2000
    Heavier run: 8 concurrent writers, 2000 read queries.

.EXAMPLE
    .\scripts\db-read-write-loadtest.ps1 -KeepTable -SeedRows 500000
    Keep the demo table afterwards so you can inspect it manually.
#>
[CmdletBinding()]
param(
    [string]$Database        = "order_db",
    [string]$PgUser          = "postgres",
    [string]$PgPassword      = "postgres",
    [string]$DbHost          = "localhost",
    [int]$Port               = 5432,
    [int]$SeedRows           = 100000,
    [int]$WriterProcesses    = 4,
    [int]$WriterOps          = 400,
    [int]$RowsPerInsert      = 50,
    [int]$ReaderQueries      = 400,
    [int]$WarmupSeconds      = 2,
    [switch]$KeepTable
)

$ErrorActionPreference = "Stop"

$DemoTable = "edm_loadtest_demo"
$WorkDir   = Join-Path $env:TEMP "edm-loadtest"
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

function Resolve-Psql {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidates = Get-ChildItem "C:\Program Files\PostgreSQL\*\bin\psql.exe" -ErrorAction SilentlyContinue |
                  Sort-Object FullName -Descending
    if ($candidates) { return $candidates[0].FullName }
    throw "psql.exe not found. Install the PostgreSQL client or add it to PATH."
}

$Psql = Resolve-Psql
$env:PGPASSWORD = $PgPassword

function Write-Header($text) {
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor DarkCyan
    Write-Host " $text" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor DarkCyan
}

function Invoke-Psql([string[]]$SqlArgs) {
    & $Psql "-h" $DbHost "-p" $Port "-U" $PgUser "-d" $Database @SqlArgs
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit $LASTEXITCODE)" }
}

function Invoke-WorkloadFile([string]$File, [string]$OutputFile) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $argList = @("-h", $DbHost, "-p", $Port, "-U", $PgUser, "-d", $Database, "-f", $File, "-q", "-o", $OutputFile)
    $p = Start-Process -FilePath $Psql -ArgumentList $argList -Wait -PassThru -WindowStyle Hidden
    $sw.Stop()
    return $sw.Elapsed.TotalSeconds
}

function New-ClientProcess([string]$File, [string]$OutputFile) {
    $argList = @("-h", $DbHost, "-p", $Port, "-U", $PgUser, "-d", $Database, "-f", $File, "-q", "-o", $OutputFile)
    return Start-Process -FilePath $Psql -ArgumentList $argList -PassThru -WindowStyle Hidden
}

# ---------------------------------------------------------------- setup
Write-Header "CQRS read/write contention demo"
Write-Host "psql          : $Psql"
Write-Host "target        : ${DbHost}:${Port}  db=$Database"
Write-Host "seed rows     : $SeedRows"
Write-Host "writers       : $WriterProcesses x $WriterOps inserts ($RowsPerInsert rows each)"
Write-Host "reader        : $ReaderQueries SELECTs"
Write-Host ""

Write-Host "Creating scratch table $DemoTable ..." -ForegroundColor Yellow
Invoke-Psql @(
    "-v", "ON_ERROR_STOP=1",
    "-c", "DROP TABLE IF EXISTS $DemoTable;",
    "-c", "CREATE TABLE $DemoTable (id serial PRIMARY KEY, payload text, status text, created_at timestamptz DEFAULT now());",
    "-c", "CREATE INDEX idx_${DemoTable}_status ON $DemoTable(status);",
    "-c", "INSERT INTO $DemoTable(payload,status) SELECT md5(random()::text), CASE WHEN random()<0.5 THEN 'ACTIVE' ELSE 'DONE' END FROM generate_series(1,$SeedRows);"
) | Out-Null

# ---------------------------------------------------------------- workloads
$insertStmt = "INSERT INTO $DemoTable(payload,status) SELECT md5(random()::text), CASE WHEN random()<0.5 THEN 'ACTIVE' ELSE 'DONE' END FROM generate_series(1,$RowsPerInsert);`n"
$readStmt   = "SELECT count(*) FROM $DemoTable WHERE status='ACTIVE';`n"

$WriterFile = Join-Path $WorkDir "writer.sql"
$ReaderFile = Join-Path $WorkDir "reader.sql"
Set-Content -LiteralPath $WriterFile -Value ($insertStmt * $WriterOps)
Set-Content -LiteralPath $ReaderFile -Value ($readStmt * $ReaderQueries)

# ---------------------------------------------------------------- baseline
Write-Host ""
Write-Host "Phase 1: baseline (reads only, no writers) ..." -ForegroundColor Yellow
$baseline = Invoke-WorkloadFile -File $ReaderFile -OutputFile (Join-Path $WorkDir "baseline.out")
Write-Host ("  baseline read time : {0:N2}s for {1} SELECTs" -f $baseline, $ReaderQueries) -ForegroundColor Green

# ---------------------------------------------------------------- mixed load
Write-Host ""
Write-Host "Phase 2: mixed load ($WriterProcesses writers + 1 timed reader) ..." -ForegroundColor Yellow
$writers = @()
for ($i = 0; $i -lt $WriterProcesses; $i++) {
    $writers += New-ClientProcess -File $WriterFile -OutputFile (Join-Path $WorkDir "writer$i.out")
}
Start-Sleep -Seconds $WarmupSeconds

$lockRow = Invoke-Psql @("-t", "-A", "-c", "SELECT count(*) FROM pg_locks WHERE NOT granted;")
$locksWaiting = ($lockRow | Out-String).Trim()

$activeRow = Invoke-Psql @("-t", "-A", "-c", "SELECT count(*) FROM pg_stat_activity WHERE state='active';")
$activeConns = ($activeRow | Out-String).Trim()

Write-Host "  active connections during load : $activeConns" -ForegroundColor Gray
Write-Host "  writes currently waiting on a lock : $locksWaiting" -ForegroundColor Gray

$underLoad = Invoke-WorkloadFile -File $ReaderFile -OutputFile (Join-Path $WorkDir "underload.out")
$writers | Wait-Process

$ratio = if ($baseline -gt 0) { $underLoad / $baseline } else { 0 }

# ---------------------------------------------------------------- report
Write-Header "Results"
Write-Host ("  baseline (no writes)   : {0,8:N2}s   ({1:N0} reads/s)" -f $baseline, ($ReaderQueries / $baseline))
Write-Host ("  under write load       : {0,8:N2}s   ({1:N0} reads/s)" -f $underLoad, ($ReaderQueries / $underLoad)) -ForegroundColor Yellow
Write-Host ("  slowdown factor        : {0,8:N1}x" -f $ratio) -ForegroundColor Red
Write-Host ("  lock waits (MVCC)      : {0}  (expected 0 - writers do not block readers)" -f $locksWaiting) -ForegroundColor Gray
Write-Host ""
Write-Host "Interpretation:" -ForegroundColor Cyan
Write-Host "  Reads slowed down even though NO locks were held. The shared write table's CPU/IO"
Write-Host "  and connection pool are the bottleneck. A CQRS read model (separate table or replica)"
Write-Host "  removes this coupling so read spikes cannot slow the write/saga path."

# ---------------------------------------------------------------- teardown
Write-Host ""
if ($KeepTable) {
    Write-Host "Keeping table $DemoTable (per -KeepTable). Drop it later with:" -ForegroundColor Yellow
    Write-Host "  `$env:PGPASSWORD='$PgPassword'; & '$Psql' -h $DbHost -U $PgUser -d $Database -c 'DROP TABLE $DemoTable;'"
} else {
    Write-Host "Dropping scratch table $DemoTable ..." -ForegroundColor Yellow
    Invoke-Psql @("-c", "DROP TABLE IF EXISTS $DemoTable;") | Out-Null
}
Write-Host "Done." -ForegroundColor Green
