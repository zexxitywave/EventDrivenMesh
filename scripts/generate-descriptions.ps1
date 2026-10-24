param()

$OllamaUrl = "http://localhost:11434/api/generate"
$Model     = "qwen2.5:3b"

function Get-UniqueProducts {
    $rows = @(docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c "SELECT DISTINCT name, category FROM csv_products WHERE description IS NULL ORDER BY name, category;" 2>$null)
    return $rows | ForEach-Object { $p = $_ -split "\|", 2; [pscustomobject]@{ Name = $p[0]; Category = $p[1] } }
}

function Generate-Description([string]$name, [string]$category) {
    $prompt = "Write one realistic product description for a product called '$name' in the category '$category'. 1-2 sentences. Mention typical features, materials or technology, and a use case. Do not mention price or brand. Return only the description text."

    $body = @{
        model  = $Model
        prompt = $prompt
        stream = $false
        options = @{ temperature = 0.7; num_predict = 120 }
    } | ConvertTo-Json -Depth 5

    $resp = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 120
    return $resp.response.Trim()
}

$products = Get-UniqueProducts
$total = $products.Count
Write-Host "Unique products to describe: $total"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$done = 0

foreach ($p in $products) {
    $desc = Generate-Description $p.Name $p.Category

    $escapedName = $p.Name -replace "'", "''"
    $escapedCategory = $p.Category -replace "'", "''"
    $escapedDesc = $desc -replace "'", "''"

    docker exec postgres psql -U postgres -d product_db -c "UPDATE csv_products SET description = '$escapedDesc' WHERE name = '$escapedName' AND category = '$escapedCategory';" 2>$null | Out-Null

    $done++
    Write-Host ("  [{0}/{1}] {2} ({3})" -f $done, $total, $p.Name, $p.Category)
}

Write-Host ("DONE in {0:0.0}s - all rows have descriptions." -f $sw.Elapsed.TotalSeconds)