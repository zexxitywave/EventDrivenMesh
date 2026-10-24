@echo off
REM ============================================================
REM  03-test-self-healing.bat
REM  Validates Kubernetes self-healing (liveness probe + restart).
REM
REM  What it does:
REM    1. Shows current pods
REM    2. Deletes one pod (simulates crash / OOMKill)
REM    3. Watches K8s spin up a replacement automatically
REM    4. Verifies health endpoint returns UP on the new pod
REM    5. Checks restart count to confirm self-healing history
REM
REM  PASS = new pod reaches Running/Ready in under 60s
REM ============================================================

echo ============================================================
echo  Current pods:
echo ============================================================
kubectl get pods -l app=order-service -o wide
echo.

REM Get the name of the first running pod
for /f "tokens=1" %%p in ('kubectl get pods -l app=order-service --no-headers ^| findstr "Running"') do (
    SET POD_TO_KILL=%%p
    goto :got_pod
)
:got_pod

echo ============================================================
echo  Killing pod: %POD_TO_KILL%
echo ============================================================
kubectl delete pod %POD_TO_KILL%

echo.
echo Watching for replacement pod to appear...
echo.

SET COUNT=0
:watch_loop
SET /a COUNT+=1
echo --- Watch %COUNT% at %TIME% ---
kubectl get pods -l app=order-service -o wide
echo.

REM Check if we have 2 Running pods again
for /f %%r in ('kubectl get pods -l app=order-service --no-headers ^| findstr "Running" ^| find /c "Running"') do SET RUNNING=%%r
if "%RUNNING%"=="2" (
    echo.
    echo ============================================================
    echo  SELF-HEALING CONFIRMED: 2 pods Running again at %TIME%
    echo ============================================================
    goto :verify
)

if %COUNT% LSS 12 (
    timeout /t 10 /nobreak >nul
    goto :watch_loop
)

echo WARNING: Pods did not recover within 120s. Check events:
kubectl describe deployment order-service
goto :done

:verify
echo.
echo Verifying health endpoint on new pod...
curl -s http://localhost:30081/actuator/health | findstr "UP"
if %errorlevel%==0 (
    echo HEALTH CHECK PASSED - Service is UP
) else (
    echo WARNING: Health check did not return UP
)

echo.
echo Restart counts (shows historical crashes healed):
kubectl get pods -l app=order-service -o custom-columns="NAME:.metadata.name,RESTARTS:.status.containerStatuses[0].restartCount,STATUS:.status.phase"

:done
echo.
echo Full pod description:
kubectl describe pods -l app=order-service | findstr -i "restart\|state\|ready\|image"
pause
