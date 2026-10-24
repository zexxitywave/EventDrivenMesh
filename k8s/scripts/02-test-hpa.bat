@echo off
REM ============================================================
REM  02-test-hpa.bat
REM  Validates Horizontal Pod Autoscaler.
REM
REM  What it does:
REM    1. Shows current replica count (should be 2)
REM    2. Uses kubectl run to launch a stress pod that hammers
REM       the order-service with CPU-intensive requests
REM    3. Watches HPA every 15s for 4 minutes
REM    4. Cleans up the stress pod
REM    5. Watches HPA scale back down
REM
REM  PASS = replicas climbed above 2, then returned to 2
REM
REM  NOTE: metrics-server must be installed for HPA to work.
REM  Install on Docker Desktop:
REM    kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
REM    kubectl patch deployment metrics-server -n kube-system \
REM      --type=json -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
REM ============================================================

echo ============================================================
echo  Current state before stress test:
echo ============================================================
kubectl get hpa order-service-hpa
kubectl get pods -l app=order-service
echo.

echo ============================================================
echo  Launching stress pod inside cluster to generate CPU load...
echo ============================================================
kubectl run order-stress ^
  --image=busybox ^
  --restart=Never ^
  --rm ^
  -it ^
  -- sh -c "while true; do wget -q -O- http://order-service:8081/api/orders; done" &

echo.
echo Stress pod launched. Watching HPA for 4 minutes...
echo (Look for REPLICAS column to increase beyond 2)
echo.

SET COUNT=0
:watch_loop
SET /a COUNT+=1
echo --- Watch %COUNT% at %TIME% ---
kubectl get hpa order-service-hpa
kubectl get pods -l app=order-service --no-headers ^| find /c "Running"
echo.
if %COUNT% LSS 16 (
    timeout /t 15 /nobreak >nul
    goto :watch_loop
)

echo ============================================================
echo  Stopping stress pod...
echo ============================================================
kubectl delete pod order-stress --ignore-not-found

echo.
echo ============================================================
echo  Watching scale-DOWN (stabilization window = 120s)
echo  This will take ~3 minutes...
echo ============================================================
SET COUNT=0
:scaledown_loop
SET /a COUNT+=1
echo --- ScaleDown Watch %COUNT% at %TIME% ---
kubectl get hpa order-service-hpa
if %COUNT% LSS 12 (
    timeout /t 30 /nobreak >nul
    goto :scaledown_loop
)

echo.
echo Final state:
kubectl get hpa order-service-hpa
kubectl get pods -l app=order-service -o wide
pause
