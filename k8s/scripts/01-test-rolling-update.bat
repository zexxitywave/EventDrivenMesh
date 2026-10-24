@echo off
REM ============================================================
REM  01-test-rolling-update.bat
REM  PROVES zero-downtime deployment.
REM
REM  What it does:
REM    1. Starts a background curl loop hitting /api/orders every 500ms
REM    2. Triggers a rolling update (image tag bump)
REM    3. Waits for rollout to complete
REM    4. Stops the curl loop and reports any errors found
REM
REM  PASS = zero non-2xx responses during the rollout window
REM  FAIL = any 5xx seen = downtime occurred
REM ============================================================

SET TOKEN=REPLACE_WITH_VALID_JWT
SET ERROR_COUNT=0
SET LOG_FILE=rolling-update-errors.log
SET RUNNING_FLAG=rolling_test_running.flag

echo. > %LOG_FILE%
echo. > %RUNNING_FLAG%

echo ============================================================
echo  Starting continuous traffic in background...
echo  Target: http://localhost:30081/api/orders
echo ============================================================

REM Start background traffic loop in a separate window
start "Rolling Update Traffic" cmd /c "01-traffic-loop.bat %TOKEN% %RUNNING_FLAG% %LOG_FILE%"

timeout /t 5 /nobreak >nul

echo.
echo ============================================================
echo  TRIGGERING ROLLING UPDATE NOW...
echo  (Simulated by patching a new env var — forces pod restart)
echo ============================================================

kubectl patch deployment order-service --patch ^
  "{\"spec\":{\"template\":{\"metadata\":{\"annotations\":{\"rollout-trigger\":\"%DATE% %TIME%\"}}}}}"

echo.
echo Watching rollout status (this will take ~30-60 seconds)...
kubectl rollout status deployment/order-service --timeout=180s

echo.
echo ============================================================
echo  Rollout complete. Stopping traffic loop...
echo ============================================================
del %RUNNING_FLAG% 2>nul
timeout /t 3 /nobreak >nul

echo.
echo ── Error log from traffic loop ─────────────────────────────
type %LOG_FILE%
echo ────────────────────────────────────────────────────────────

echo.
echo Final pod state:
kubectl get pods -l app=order-service -o wide

echo.
echo Rollout history:
kubectl rollout history deployment/order-service

echo.
echo ============================================================
echo  If the error log above is empty = ZERO DOWNTIME ACHIEVED
echo ============================================================
pause
