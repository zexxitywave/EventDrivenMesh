$base = "http://localhost:8093/actuator/metrics"
$prev = 0

for ($i = 0; $i -lt 30; $i++) {
    try {
        $count = (Invoke-RestMethod ($base + "/spring.kafka.listener") -ErrorAction Stop).measurements[0].value
        $cpu   = [math]::Round((Invoke-RestMethod ($base + "/process.cpu.usage")).measurements[0].value * 100, 2)
        $mem   = [math]::Round((Invoke-RestMethod ($base + "/jvm.memory.used")).measurements[0].value / 1MB, 1)
        $diff  = $count - $prev
        $prev  = $count
        $time  = Get-Date -Format "HH:mm:ss"
        Write-Host "$time | total=$count | per_sec=$diff | cpu=$cpu% | mem=${mem}MB"
        if ($count -gt 19700) {
            Write-Host "ALL DONE — all events consumed!"
            break
        }
    } catch {
        Write-Host "waiting for service..."
    }
    Start-Sleep -Seconds 1
}
