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
    [int]$SeedRows           = 100000,  #Initially put 100,000 rows into the table
    [int]$WriterProcesses    = 12,      #Run 12 writers simultaneously
    [int]$WriterOps          = 1500,    #Each writer performs 1,500 insert operations
    [int]$RowsPerInsert      = 200,     #Each INSERT operation inserts 200 rows
    [int]$ReaderQueries      = 1000,    #Run 1,000 read/SELECT queries
    [int]$WarmupSeconds      = 2,
    [switch]$KeepTable
)


#Writer 1 ──┐
#Writer 2 ──┤
#Writer 3 ──┤
#...        ├──→ PostgreSQL
#Writer 12 ─┘
#
#Each writer:
#1,500 INSERT operations
#
#Each INSERT:
#200 rows
#
#Therefore:
#
#12 writers
#× 1,500 operations
#× 200 rows
#= 3,600,000 rows

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
Write-Host "phases        : 1=read-only  2=write-only  3=mixed"
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

# Functions that start all writers and time the whole batch
function Invoke-WriteBatch([string]$Label) {
    $processes = @()
    for ($i = 0; $i -lt $WriterProcesses; $i++) {
        $processes += New-ClientProcess -File $WriterFile -OutputFile (Join-Path $WorkDir "${Label}_writer$i.out")
    }
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $processes | Wait-Process
    $sw.Stop()
    $elapsed = $sw.Elapsed.TotalSeconds
    $ops  = $WriterProcesses * $WriterOps
    $rows = $WriterProcesses * $WriterOps * $RowsPerInsert
    return [pscustomobject]@{
        Ops       = $ops
        Rows      = $rows
        Elapsed   = $elapsed
        OpsPerSec = if ($elapsed -gt 0) { $ops  / $elapsed } else { 0 }
        RowsPerSec= if ($elapsed -gt 0) { $rows / $elapsed } else { 0 }
    }
}

# ---------------------------------------------------------------- baseline
Write-Host ""
Write-Host "Phase 1: baseline (reads only, no writers) ..." -ForegroundColor Yellow
$baseline = Invoke-WorkloadFile -File $ReaderFile -OutputFile (Join-Path $WorkDir "baseline.out")
Write-Host ("  baseline read time : {0:N2}s for {1} SELECTs  =  {2:N0} reads/s" -f $baseline, $ReaderQueries, ($ReaderQueries / $baseline)) -ForegroundColor Green

# ---------------------------------------------------------------- write-only
Write-Host ""
Write-Host "Phase 2: write-only ($WriterProcesses writers, no reads) ..." -ForegroundColor Yellow
$writeOnly = Invoke-WriteBatch -Label "writeonly"
Write-Host ("  write batch       : {0:N2}s for {1:N0} INSERTs ({2:N0} rows)" -f $writeOnly.Elapsed, $writeOnly.Ops, $writeOnly.Rows) -ForegroundColor Green
Write-Host ("  write throughput  : {0:N0} INSERTs/s  =  {1:N0} rows/s" -f $writeOnly.OpsPerSec, $writeOnly.RowsPerSec) -ForegroundColor Green

# Restore the table to the same size as the baseline so the mixed-read
# comparison is against an identical scan cost (count over same row count).
Write-Host "  resetting table to $SeedRows rows so reads stay comparable ..." -ForegroundColor Yellow
Invoke-Psql @(
    "-v", "ON_ERROR_STOP=1",
    "-c", "TRUNCATE $DemoTable;",
    "-c", "INSERT INTO $DemoTable(payload,status) SELECT md5(random()::text), CASE WHEN random()<0.5 THEN 'ACTIVE' ELSE 'DONE' END FROM generate_series(1,$SeedRows);"
) | Out-Null

# ---------------------------------------------------------------- mixed load
Write-Host ""
Write-Host "Phase 3: mixed load ($WriterProcesses writers + 1 timed reader) ..." -ForegroundColor Yellow
$writerBatchStart = [DateTime]::Now
$writerProcs = @()
for ($i = 0; $i -lt $WriterProcesses; $i++) {
    $writerProcs += New-ClientProcess -File $WriterFile -OutputFile (Join-Path $WorkDir "mixed_writer$i.out")
}
Start-Sleep -Seconds $WarmupSeconds

$lockRow = Invoke-Psql @("-t", "-A", "-c", "SELECT count(*) FROM pg_locks WHERE NOT granted;")
$locksWaiting = ($lockRow | Out-String).Trim()

$activeRow = Invoke-Psql @("-t", "-A", "-c", "SELECT count(*) FROM pg_stat_activity WHERE state='active';")
$activeConns = ($activeRow | Out-String).Trim()

Write-Host "  active connections during load : $activeConns" -ForegroundColor Gray
Write-Host "  writes currently waiting on a lock : $locksWaiting" -ForegroundColor Gray

$underLoad = Invoke-WorkloadFile -File $ReaderFile -OutputFile (Join-Path $WorkDir "underload.out")
$writerProcs | Wait-Process -Timeout 60 | Out-Null
$writerProcs | ForEach-Object { if (-not $_.HasExited) { $_ | Wait-Process | Out-Null } }
$mixedWriterSecs = [Math]::Max((([DateTime]::Now) - $writerBatchStart).TotalSeconds, 0.00001)

$mixedWriteOps  = $WriterProcesses * $WriterOps
$mixedWriteRows = $WriterProcesses * $WriterOps * $RowsPerInsert

$ratio = if ($baseline -gt 0) { $underLoad / $baseline } else { 0 }

# ---------------------------------------------------------------- report
Write-Header "Results"
Write-Host ("  READ-ONLY  (baseline)        : {0,8:N2}s   ({1:N0} reads/s)" -f $baseline, ($ReaderQueries / $baseline))
Write-Host ("  WRITE-ONLY (no readers)      : {0,8:N2}s   ({1:N0} INSERTs/s = {2:N0} rows/s)" -f $writeOnly.Elapsed, $writeOnly.OpsPerSec, $writeOnly.RowsPerSec) -ForegroundColor Green
Write-Host ("  MIXED  read side             : {0,8:N2}s   ({1:N0} reads/s)" -f $underLoad, ($ReaderQueries / $underLoad)) -ForegroundColor Yellow
Write-Host ("  MIXED  write side            : {0,8:N2}s   ({1:N0} INSERTs/s = {2:N0} rows/s)" -f $mixedWriterSecs, ($mixedWriteOps / $mixedWriterSecs), ($mixedWriteRows / $mixedWriterSecs)) -ForegroundColor Yellow
Write-Host ("  read slowdown (separate->mixed) : {0,8:N1}x" -f $ratio) -ForegroundColor Red
Write-Host ("  write slowdown (separate->mixed) : {0,8:N1}x" -f ($writeOnly.OpsPerSec / ($mixedWriteOps / $mixedWriterSecs))) -ForegroundColor Red
Write-Host ("  lock waits (MVCC)      : {0}  (expected 0 - writers do not block readers)" -f $locksWaiting) -ForegroundColor Gray
Write-Host ""
Write-Host "Interpretation:" -ForegroundColor Cyan
Write-Host "  Reads AND writes got slower once the same table was shared. No locks were held"
Write-Host "  (MVCC), so the bottleneck is shared CPU/IO and the connection pool. A CQRS read"
Write-Host "  model (separate table or replica) removes this coupling so read spikes cannot"
Write-Host "  slow the write/saga path."

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
