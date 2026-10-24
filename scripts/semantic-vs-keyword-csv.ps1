param(
    [string[]]$Queries = @("wireless sound", "winter jacket", "red wine", "protein shake", "warm blanket for winter nights", "night shift eye strain", "banana", "mountain bike", "car oil filter"),
    [int]$TopN = 5
)

$OllamaUrl = "http://localhost:11434/api/embed"
$Model     = "nomic-embed-text"

function Get-QueryVector([string]$text) {
    $body = @{ model = $Model; input = $text } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 60
    return "[" + ($resp.embeddings[0] -join ",") + "]"
}

function Get-SqlResult([string]$sql) {
    $rows = docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c $sql 2>$null
    if ($null -eq $rows) { return @() }
    return @($rows | Where-Object { $_ -and $_.Trim() })
}

Write-Host ("=" * 78)
Write-Host ("{0,-34} {1,10} {2,10}   {3}" -f "QUERY", "ILIKE hits", "semantic", "verdict")
Write-Host ("=" * 78)

foreach ($q in $Queries) {
    $vec  = Get-QueryVector $q
    $top  = "SELECT name, category, round((1-(embedding <=> '$vec'::vector))::numeric,3) AS score FROM csv_products ORDER BY embedding <=> '$vec'::vector LIMIT $TopN;"
    $like = "SELECT count(*) FROM csv_products WHERE concat(name,' ',category) ILIKE '%$q%';"

    $rows      = Get-SqlResult $top
    $likeCount = (Get-SqlResult $like | Select-Object -First 1)

    $verdict = "semantic ONLY"
    if ($likeCount -gt 0) { $verdict = "both work" }
    if ($rows.Count -eq 0) { $verdict = "nothing relevant (low score)" }

    Write-Host ("{0,-34} {1,10} {2,10}   {3}" -f $q, $likeCount, $rows.Count, $verdict)
    $rows | ForEach-Object { Write-Host ("      " + $_) }
    Write-Host ""
}

Write-Host "Try your own:"
Write-Host "  & .\scripts\semantic-vs-keyword-csv.ps1 -Queries @('espresso beans','kids toy','something to drink from')"