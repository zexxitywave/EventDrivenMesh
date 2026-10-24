$filePath = "c:\Users\jagdish\Downloads\ecommerce-microservices-main\scripts\create-multiple-postgres-dbs.sh"
$content = [System.IO.File]::ReadAllText($filePath)
$fixed = $content.Replace("`r`n", "`n").Replace("`r", "`n")
[System.IO.File]::WriteAllText($filePath, $fixed, [System.Text.Encoding]::UTF8)
Write-Host "Line endings fixed - now LF only"
