@echo off
REM ============================================================
REM  05-test-resource-limits.bat
REM  Validates resource requests/limits and shows live usage.
REM
REM  What it does:
REM    1. Shows configured requests/limits from the deployment
REM    2. Shows live CPU & memory usage per pod (needs metrics-server)
REM    3. Shows node resource capacity vs allocatable
REM    4. Shows what percentage of node resources order-service uses
REM ============================================================

echo ============================================================
echo  Configured resource requests and limits:
echo ============================================================
kubectl get deployment order-service -o jsonpath=^
"{range .spec.template.spec.containers[*]}{.name}{'\n'}  Requests: CPU={.resources.requests.cpu} MEM={.resources.requests.memory}{'\n'}  Limits:   CPU={.resources.limits.cpu}   MEM={.resources.limits.memory}{'\n'}{end}"
echo.

echo ============================================================
echo  Live resource usage per pod (requires metrics-server):
echo ============================================================
kubectl top pods -l app=order-service
echo.

echo ============================================================
echo  Node capacity:
echo ============================================================
kubectl top nodes
echo.
kubectl describe nodes | findstr -i "cpu\|memory\|capacity\|allocatable"

echo.
echo ============================================================
echo  Quality of Service (QoS) class for order-service pods:
echo  - Guaranteed = requests == limits (most stable)
echo  - Burstable   = requests < limits  (our config)
echo  - BestEffort  = no requests/limits (avoid in prod)
echo ============================================================
for /f "tokens=1" %%p in ('kubectl get pods -l app=order-service --no-headers') do (
    echo Pod: %%p
    kubectl get pod %%p -o jsonpath="{.status.qosClass}"
    echo.
)
pause
