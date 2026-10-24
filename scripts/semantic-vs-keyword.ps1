param(
    [string[]]$Queries = @("noise cancelling", "gaming", "pro gaming setup", "wifi router", "tasty banana", "headphones", "cheap samsung phone", "gaiming")
)

$OllamaUrl  = "http://localhost:11434/api/embed"
$Model      = "nomic-embed-text"

function Get-QueryVector([string]$text) {
    $body = @{ model = $Model; input = $text } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 60
    return "[" + ($resp.embeddings[0] -join ",") + "]"
}

function Get-SqlResult([string]$sql) {
    $rows = docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c $sql 2>$null
    return @($rows | Where-Object { $_ -and $_.Trim() })
}

Write-Host ("=" * 78)
Write-Host ("{0,-34} {1,10} {2,10}   {3}" -f "QUERY", "ILIKE hits", "semantic", "verdict")
Write-Host ("=" * 78)

foreach ($q in $Queries) {
    $vec = Get-QueryVector $q
    $top = "SELECT name, round((1-(embedding <=> '$vec'::vector))::numeric,3) AS score FROM products ORDER BY embedding <=> '$vec'::vector LIMIT 4;"
    $like = "SELECT count(*) FROM products WHERE concat(name,' ',COALESCE(description,'')) ILIKE '%$q%';"

    $rows = Get-SqlResult $top
    $likeCount = (Get-SqlResult $like | Select-Object -First 1)

$names = if ($rows.Count -gt 0) { ($rows | ForEach-Object { ($_ -split "\|")[0] }) -join ", " } else { "-" }
    $verdict = "semantic ONLY" 
    if ($likeCount -gt 0) { $verdict = "both work" }
    if ($rows.Count -eq 0) { $verdict = "nothing relevant (low score)" }

    Write-Host ("{0,-34} {1,10} {2,10}   {3}" -f $q, $likeCount, $rows.Count, $verdict)
    if ($rows.Count -gt 0) {
        $rows | ForEach-Object { Write-Host ("      " + $_) }
    }
    Write-Host ""
}
Write-Host "- to try another query one-off, in PowerShell run:"
Write-Host "      & .\scripts\semantic-vs-keyword.ps1 -Queries @('open ear buds','gaming mouse')"
