@echo off
REM ============================================================
REM  06-test-rollback.bat
REM  Validates kubectl rollout undo (instant rollback).
REM
REM  What it does:
REM    1. Shows rollout history
REM    2. Simulates a bad deploy (broken image tag)
REM    3. Watches pods crash (ImagePullBackOff expected)
REM    4. Rolls back to previous revision
REM    5. Confirms service is healthy again
REM ============================================================

echo ============================================================
echo  Rollout history BEFORE bad deploy:
echo ============================================================
kubectl rollout history deployment/order-service
echo.

echo Current healthy pods:
kubectl get pods -l app=order-service
echo.

echo ============================================================
echo  Deploying BAD image (intentionally broken tag)...
echo ============================================================
kubectl set image deployment/order-service order-service=order-service:broken-tag-does-not-exist

echo.
echo Watching for failure (ImagePullBackOff / ErrImagePull expected)...
timeout /t 5 /nobreak >nul
SET COUNT=0
:watch_bad
SET /a COUNT+=1
kubectl get pods -l app=order-service
echo.
REM Look for crash signals
kubectl get pods -l app=order-service --no-headers | findstr /i "ErrImage\|ImagePull\|CrashLoop\|Pending"
if %errorlevel%==0 (
    echo BAD DEPLOY CONFIRMED - pods are failing as expected.
    goto :rollback
)
if %COUNT% LSS 6 (
    timeout /t 5 /nobreak >nul
    goto :watch_bad
)

:rollback
echo.
echo ============================================================
echo  ROLLING BACK to previous revision...
echo ============================================================
kubectl rollout undo deployment/order-service

echo.
echo Watching rollback status...
kubectl rollout status deployment/order-service --timeout=120s

echo.
echo ============================================================
echo  Rollback complete. Verifying service health...
echo ============================================================
timeout /t 5 /nobreak >nul
kubectl get pods -l app=order-service -o wide
echo.
curl -s http://localhost:30081/actuator/health | findstr "UP"
if %errorlevel%==0 (
    echo HEALTH CHECK PASSED after rollback
) else (
    echo WARNING: Health check did not return UP
)

echo.
echo Rollout history AFTER rollback:
kubectl rollout history deployment/order-service
pause
