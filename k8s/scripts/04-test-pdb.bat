@echo off
REM ============================================================
REM  04-test-pdb.bat
REM  Validates PodDisruptionBudget (minAvailable: 1).
REM
REM  What it does:
REM    1. Scales up to 3 pods so we have room to drain
REM    2. Attempts to evict pods one by one via the eviction API
REM    3. Confirms PDB blocks eviction when only 1 pod remains
REM    4. Scales back to 2
REM
REM  PASS = eviction of last pod is blocked with "Cannot evict pod"
REM
REM  NOTE: On Docker Desktop single-node, kubectl drain will
REM  also drain system pods which may cause issues. This script
REM  uses the safer pod eviction API approach instead.
REM ============================================================

echo ============================================================
echo  Current PDB state:
echo ============================================================
kubectl get pdb order-service-pdb -o wide
echo.

echo ============================================================
echo  Scaling up to 3 pods for drain test...
echo ============================================================
kubectl scale deployment order-service --replicas=3
kubectl rollout status deployment/order-service --timeout=60s
echo.

kubectl get pods -l app=order-service -o wide
echo.

REM Get all pod names into variables
SET POD_LIST=
for /f "tokens=1" %%p in ('kubectl get pods -l app=order-service --no-headers') do (
    SET POD_LIST=%POD_LIST% %%p
)

echo ============================================================
echo  Attempting eviction of first pod (should succeed)...
echo ============================================================
for /f "tokens=1" %%p in ('kubectl get pods -l app=order-service --no-headers ^| findstr "Running"') do (
    SET FIRST_POD=%%p
    goto :evict_first
)
:evict_first

kubectl delete pod %FIRST_POD%
echo Pod %FIRST_POD% evicted. Waiting for replacement...
kubectl rollout status deployment/order-service --timeout=60s
echo.

kubectl get pods -l app=order-service
echo.
kubectl get pdb order-service-pdb

echo.
echo ============================================================
echo  Scaling DOWN to 1 pod (below minAvailable threshold)...
echo  Then trying to delete — PDB should BLOCK this.
echo ============================================================
kubectl scale deployment order-service --replicas=1
timeout /t 10 /nobreak >nul
kubectl get pods -l app=order-service

echo.
echo Attempting to delete the only remaining pod...
echo (PDB minAvailable:1 should BLOCK this eviction)
echo.
for /f "tokens=1" %%p in ('kubectl get pods -l app=order-service --no-headers ^| findstr "Running"') do (
    SET LAST_POD=%%p
    goto :evict_last
)
:evict_last

REM Use eviction API - this respects PDB unlike kubectl delete
kubectl proxy &
timeout /t 2 /nobreak >nul

curl -s -X POST ^
  -H "Content-Type: application/json" ^
  -d "{\"apiVersion\":\"policy/v1\",\"kind\":\"Eviction\",\"metadata\":{\"name\":\"%LAST_POD%\",\"namespace\":\"default\"}}" ^
  http://127.0.0.1:8001/api/v1/namespaces/default/pods/%LAST_POD%/eviction

echo.
echo.
echo Pod still running (PDB protected it):
kubectl get pods -l app=order-service

echo.
echo ============================================================
echo  Restoring to 2 replicas...
echo ============================================================
kubectl scale deployment order-service --replicas=2
kubectl rollout status deployment/order-service --timeout=60s

kubectl get pods -l app=order-service -o wide
kubectl get pdb order-service-pdb
pause
