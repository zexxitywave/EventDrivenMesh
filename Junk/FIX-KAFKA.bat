@echo off
echo.
echo ========================================
echo   KAFKA FULL RESET
echo ========================================
echo.

echo [1] Stopping all containers...
docker compose down
timeout /t 3 /nobreak >nul

echo.
echo [2] Removing ALL kafka volumes...
for /f "tokens=*" %%i in ('docker volume ls -q') do (
    echo %%i | findstr /i "kafka" >nul && (
        echo Removing: %%i
        docker volume rm %%i
    )
)
docker volume prune -f

echo.
echo [3] Starting fresh...
docker compose up -d

echo.
echo [4] Waiting 25 seconds for Kafka to start...
timeout /t 25 /nobreak

echo.
echo [5] Container status:
docker ps

echo.
echo [6] Kafka logs:
docker logs kafka --tail 30

echo.
echo ========================================
echo  DONE - Check above for "started" 
echo  Kafka UI: http://localhost:8071
echo ========================================
pause
