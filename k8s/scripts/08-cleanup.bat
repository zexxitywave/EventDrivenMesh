@echo off
REM ============================================================
REM  08-cleanup.bat
REM  Tears down all order-service k8s resources cleanly.
REM ============================================================
echo Deleting order-service resources...
kubectl delete -f ..\order-hpa.yaml        --ignore-not-found
kubectl delete -f ..\order-pdb.yaml        --ignore-not-found
kubectl delete -f ..\order-deployment.yaml --ignore-not-found
kubectl delete -f ..\order-service.yaml    --ignore-not-found
kubectl delete -f ..\order-configmap.yaml  --ignore-not-found
kubectl delete -f ..\order-secret.yaml     --ignore-not-found
kubectl delete pod order-stress            --ignore-not-found

echo.
echo Remaining resources:
kubectl get all -l app=order-service
echo.
echo Cleanup complete.
pause
