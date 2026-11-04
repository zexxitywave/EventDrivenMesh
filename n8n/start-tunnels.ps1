# Starts four Cloudflare Quick Tunnels so n8n Cloud can reach the Spring services
# running on this machine.
#
# Prerequisite (one time):
#   winget install --id Cloudflare.cloudflared
#   (open a new terminal afterwards so PATH is refreshed)
#
# Usage:
#   .\n8n\start-tunnels.ps1
#   .\n8n\start-tunnels.ps1 -WaitSeconds 40
#
# The script prints the four URLs to paste into the n8n "Config" node.
# The tunnel URLs are temporary and change every time you restart them.

param(
    [int]$WaitSeconds = 25
)

$ErrorActionPreference = "Stop"

$tunnels = @(
    @{ Name = "cart-service";         Port = 8089; ConfigKey = "cartServiceBaseUrl" },
    @{ Name = "order-service";        Port = 8081; ConfigKey = "orderServiceBaseUrl" },
    @{ Name = "user-service";         Port = 8087; ConfigKey = "userServiceBaseUrl" },
    @{ Name = "notification-service"; Port = 8084; ConfigKey = "notificationServiceBaseUrl" }
)

$cloudflared = Get-Command cloudflared -ErrorAction SilentlyContinue
if (-not $cloudflared) {
    Write-Host "cloudflared is not installed." -ForegroundColor Yellow
    Write-Host "Install it with:" -ForegroundColor Yellow
    Write-Host "  winget install --id Cloudflare.cloudflared" -ForegroundColor Cyan
    Write-Host "Then open a new terminal and rerun this script."
    exit 1
}

$logDir = Join-Path $PSScriptRoot ".tunnels"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# Stop tunnels left over from a previous run so we don't accumulate them.
$oldPidFile = Join-Path $logDir "pids.txt"
if (Test-Path $oldPidFile) {
    foreach ($oldPid in (Get-Content $oldPidFile)) {
        $parsed = 0
        if ([int]::TryParse($oldPid.Trim(), [ref]$parsed)) {
            $old = Get-Process -Id $parsed -ErrorAction SilentlyContinue
            if ($old -and $old.ProcessName -like 'cloudflared*') {
                Stop-Process -Id $parsed -Force -ErrorAction SilentlyContinue
            }
        }
    }
    Remove-Item $oldPidFile -ErrorAction SilentlyContinue
}

foreach ($t in $tunnels) {
    $listening = Get-NetTCPConnection -LocalPort $t.Port -State Listen -ErrorAction SilentlyContinue
    if (-not $listening) {
        Write-Host ("WARNING: nothing is listening on port {0} ({1})." -f $t.Port, $t.Name) -ForegroundColor Yellow
        Write-Host "         The tunnel will return 502 until that service starts."
    }
}

$started = @()
foreach ($t in $tunnels) {
    $out = Join-Path $logDir "$($t.Name).out.log"
    $err = Join-Path $logDir "$($t.Name).err.log"
    Remove-Item $out, $err -ErrorAction SilentlyContinue

    $proc = Start-Process -FilePath $cloudflared.Source `
        -ArgumentList @("tunnel", "--url", "http://localhost:$($t.Port)", "--no-autoupdate") `
        -RedirectStandardOutput $out -RedirectStandardError $err `
        -WindowStyle Hidden -PassThru

    $started += [pscustomobject]@{
        Name      = $t.Name
        Port      = $t.Port
        ConfigKey = $t.ConfigKey
        Process   = $proc
        Out       = $out
        Err       = $err
    }
    Write-Host ("Started tunnel for {0} (port {1}), PID {2}" -f $t.Name, $t.Port, $proc.Id)
}

# cloudflared prints the assigned https://*.trycloudflare.com URL to its log.
$deadline = (Get-Date).AddSeconds($WaitSeconds)
$urls = @{}
do {
    Start-Sleep -Seconds 2
    foreach ($s in $started) {
        if ($urls.ContainsKey($s.Name)) { continue }

        $text = ""
        foreach ($file in @($s.Out, $s.Err)) {
            if (Test-Path $file) {
                $text += (Get-Content -Raw $file -ErrorAction SilentlyContinue)
            }
        }

        $match = [regex]::Match($text, 'https://[a-z0-9-]+\.trycloudflare\.com')
        if ($match.Success) { $urls[$s.Name] = $match.Value }
    }
} while ((Get-Date) -lt $deadline -and $urls.Count -lt $started.Count)

$started | ForEach-Object { $_.Process.Id } |
    Set-Content (Join-Path $logDir "pids.txt")

Write-Host ""
Write-Host "Paste these into the n8n Config node:" -ForegroundColor Green
foreach ($s in $started) {
    $url = $urls[$s.Name]
    if ($url) {
        Write-Host ("  {0,-28} = {1}" -f $s.ConfigKey, $url) -ForegroundColor Cyan
    } else {
        Write-Host ("  {0,-28} = <not ready; see {1}>" -f $s.ConfigKey, $s.Err) -ForegroundColor Yellow
    }
}

if ($urls.Count -gt 0) {
    $urls | ConvertTo-Json | Set-Content (Join-Path $logDir "urls.json")
}

Write-Host ""
Write-Host "Logs: $logDir"
Write-Host "Stop the tunnels with: .\n8n\stop-tunnels.ps1"
