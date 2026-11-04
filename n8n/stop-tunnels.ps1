# Stops the tunnels started by start-tunnels.ps1.

$logDir = Join-Path $PSScriptRoot ".tunnels"
$pidFile = Join-Path $logDir "pids.txt"

if (-not (Test-Path $pidFile)) {
    Write-Host "No tunnel PID file found. Nothing to stop."
    exit 0
}

$stopped = 0
foreach ($procId in (Get-Content $pidFile)) {
    $id = 0
    if (-not [int]::TryParse($procId.Trim(), [ref]$id)) { continue }

    $proc = Get-Process -Id $id -ErrorAction SilentlyContinue
    if ($proc -and $proc.ProcessName -like 'cloudflared*') {
        Stop-Process -Id $id -Force
        Write-Host "Stopped PID $id"
        $stopped++
    }
}

Remove-Item $pidFile -ErrorAction SilentlyContinue
Write-Host "Stopped $stopped tunnel process(es)."
