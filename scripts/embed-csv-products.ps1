param(
    [int]$BatchSize = 100
)

$OllamaUrl = "http://localhost:11434/api/embed"
$Model     = "nomic-embed-text"
$TmpFile   = Join-Path $env:TEMP "csv_products_copy.txt"
$Utf8      = New-Object System.Text.UTF8Encoding($false)

docker exec postgres psql -U postgres -d product_db -c "DROP TABLE IF EXISTS csv_products_emb; CREATE TABLE csv_products_emb (product_id int PRIMARY KEY, embedding text);" 2>$null | Out-Null

$rows = @(docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c "SELECT product_id, concat(name,' ',category) FROM csv_products ORDER BY product_id;" 2>$null)
$total = $rows.Count
Write-Host "Rows to embed: $total  (target 1000)"

if ($total -lt 1) { Write-Host "No rows found"; return }

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$done = 0

for ($off = 0; $off -lt $total; $off += $BatchSize) {
    $end = [Math]::Min($off + $BatchSize, $total) - 1
    $slice = $rows[$off..$end]

    $ids   = New-Object System.Collections.Generic.List[int]
    $texts = New-Object System.Collections.Generic.List[string]
    foreach ($r in $slice) {
        $p = $r -split "\|", 2
        $ids.Add([int]$p[0])
        $texts.Add($p[1])
    }

    $body  = @{ model = $Model; input = @($texts.ToArray()) } | ConvertTo-Json -Depth 5
    $resp  = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 120

    if ($resp.embeddings.Count -ne $texts.Count) {
        Write-Host "MISMATCH at offset $off : got $($resp.embeddings.Count) embeddings for $($texts.Count) texts"
        return
    }

    $sb = New-Object System.Text.StringBuilder
    for ($i = 0; $i -lt $texts.Count; $i++) {
        $vec = "[" + ($resp.embeddings[$i] -join ",") + "]"
        [void]$sb.AppendLine("$($ids[$i])`t$vec")
    }
    [System.IO.File]::WriteAllText($TmpFile, $sb.ToString().TrimEnd("`r","`n"), $Utf8)

    Get-Content -Raw $TmpFile | docker exec -i postgres psql -U postgres -d product_db -c "COPY csv_products_emb (product_id, embedding) FROM STDIN;" | Out-Null
    docker exec postgres psql -U postgres -d product_db -c "UPDATE csv_products e SET embedding = t.embedding::vector FROM csv_products_emb t WHERE e.product_id = t.product_id; TRUNCATE csv_products_emb;" 2>$null | Out-Null

    $done += $texts.Count
    Write-Host ("  embedded {0}/{1}  ({2:0.0}s)" -f $done, $total, $sw.Elapsed.TotalSeconds)
}

Write-Host ("DONE in {0:0.0}s" -f $sw.Elapsed.TotalSeconds)
$verified = @(docker exec postgres psql -U postgres -d product_db -t -A -c "SELECT count(*) FROM csv_products WHERE embedding IS NOT NULL;" 2>$null | Select-Object -First 1)
Write-Host "Products with embeddings stored: $verified"