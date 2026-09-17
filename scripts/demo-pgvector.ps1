# Demo script: pgvector semantic search showcase
# Run from PowerShell in project root

$ErrorActionPreference = "Continue"
$OllamaUrl = "http://localhost:11434/api/embed"
$Model = "nomic-embed-text"

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  PGVECTOR SEMANTIC SEARCH DEMO" -ForegroundColor Cyan
Write-Host "  Spring Boot + Ollama + PostgreSQL" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan

function Search-Products {
    param([string]$Query, [int]$Limit = 5)
    
    $body = @{ model = $Model; input = $Query } | ConvertTo-Json
    $e = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30
    $vec = "[" + ($e.embeddings[0] -join ",") + "]"
    
    $result = docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c "SELECT name, category, price, rating, score FROM (SELECT DISTINCT ON (name) name, category, price, rating, round((1-(embedding <=> '$vec'::vector))::numeric, 3) AS score FROM csv_products ORDER BY name, embedding <=> '$vec'::vector) ranked ORDER BY score DESC LIMIT $Limit;" 2>$null
    
    Write-Host ("  {0,-18} {1,-24} {2,8} {3,6} {4}" -f "NAME", "CATEGORY", "PRICE", "RATING", "SCORE")
    Write-Host ("  " + "-" * 72)
    
    $result | ForEach-Object {
        if ($_ -match "^([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)$") {
            Write-Host ("  {0,-18} {1,-24} {2,8} {3,6} {4}" -f $Matches[1], $Matches[2], "`$$($Matches[3])", "$($Matches[4])", $Matches[5])
        }
    }
}

function Compare-Search {
    param([string]$Query)
    
    Write-Host "QUERY: `"$Query`"" -ForegroundColor Yellow
    Write-Host ("-" * 78)
    
    # Vector search
    Write-Host "`n  VECTOR SEARCH (pgvector):" -ForegroundColor Green
    Search-Products -Query $Query -Limit 5
    
    # Keyword search
    Write-Host "`n  KEYWORD SEARCH (ILIKE):" -ForegroundColor Red
    $kwResult = docker exec postgres psql -U postgres -d product_db -t -A -F "|" -c "SELECT name, category, price, rating FROM csv_products WHERE concat(name, ' ', category, ' ', description) ILIKE '%$Query%' LIMIT 5;" 2>$null
    
    if ($kwResult) {
        Write-Host ("  {0,-18} {1,-24} {2,8} {3,6}" -f "NAME", "CATEGORY", "PRICE", "RATING")
        Write-Host ("  " + "-" * 60)
        $kwResult | ForEach-Object {
            if ($_ -match "^([^|]+)\|([^|]+)\|([^|]+)\|([^|]+)$") {
                Write-Host ("  {0,-18} {1,-24} {2,8} {3,6}" -f $Matches[1], $Matches[2], "`$$($Matches[3])", "$($Matches[4])")
            }
        }
    } else {
        Write-Host "  (no matches)" -ForegroundColor DarkGray
    }
    
    Write-Host "`n"
}

# ============================================
# DEMO 1: Synonym Matching
# ============================================
Write-Host "DEMO 1: SYNONYM MATCHING" -ForegroundColor Magenta
Write-Host "Finding products when words don't match exactly`n"
Compare-Search -Query "wireless audio"

# ============================================
# DEMO 2: Cross-Category Understanding
# ============================================
Write-Host "DEMO 2: CROSS-CATEGORY UNDERSTANDING" -ForegroundColor Magenta
Write-Host "Understanding context across product categories`n"
Compare-Search -Query "romantic evening"

# ============================================
# DEMO 3: Abstract Concepts
# ============================================
Write-Host "DEMO 3: ABSTRACT CONCEPTS" -ForegroundColor Magenta
Write-Host "Handling vague, conceptual queries`n"
Compare-Search -Query "gift for child"

# ============================================
# DEMO 4: Use-Case Based
# ============================================
Write-Host "DEMO 4: USE-CASE BASED SEARCH" -ForegroundColor Magenta
Write-Host "Search by intent, not product name`n"
Compare-Search -Query "cook healthy meal"

# ============================================
# DEMO 5: Score Differentiation
# ============================================
Write-Host "DEMO 5: SEMANTIC RANKING" -ForegroundColor Magenta
Write-Host "Products ranked by relevance`n"

$body = @{ model = $Model; input = "cozy sleep" } | ConvertTo-Json
$e = Invoke-RestMethod -Uri $OllamaUrl -Method Post -ContentType "application/json" -Body $body -TimeoutSec 30
$vec = "[" + ($e.embeddings[0] -join ",") + "]"

Write-Host "QUERY: `"cozy sleep`"" -ForegroundColor Yellow
Write-Host ("-" * 78)
Search-Products -Query "cozy sleep" -Limit 10

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  DEMO COMPLETE" -ForegroundColor Cyan
Write-Host "========================================`n" -ForegroundColor Cyan
