@echo off
REM ============================================================
REM  00-deploy-all.bat
REM  Applies every manifest in order. Run this once to bring up
REM  the full order-service stack on your local k8s cluster.
REM ============================================================
echo.
echo [STEP 1/6] Applying ConfigMap...
kubectl apply -f ..\order-configmap.yaml
if %errorlevel% neq 0 ( echo FAILED: ConfigMap & exit /b 1 )

echo [STEP 2/6] Applying Secret...
kubectl apply -f ..\order-secret.yaml
if %errorlevel% neq 0 ( echo FAILED: Secret & exit /b 1 )

echo [STEP 3/6] Applying Service (ClusterIP + NodePort)...
kubectl apply -f ..\order-service.yaml
if %errorlevel% neq 0 ( echo FAILED: Service & exit /b 1 )

echo [STEP 4/6] Applying Deployment...
kubectl apply -f ..\order-deployment.yaml
if %errorlevel% neq 0 ( echo FAILED: Deployment & exit /b 1 )

echo [STEP 5/6] Applying HPA...
kubectl apply -f ..\order-hpa.yaml
if %errorlevel% neq 0 ( echo FAILED: HPA & exit /b 1 )

echo [STEP 6/6] Applying PodDisruptionBudget...
kubectl apply -f ..\order-pdb.yaml
if %errorlevel% neq 0 ( echo FAILED: PDB & exit /b 1 )

echo.
echo ============================================================
echo  All manifests applied. Waiting for pods to be Ready...
echo ============================================================
kubectl rollout status deployment/order-service --timeout=120s

echo.
echo Current pod state:
kubectl get pods -l app=order-service -o wide

echo.
echo Services:
kubectl get svc -l app=order-service

echo.
echo HPA:
kubectl get hpa order-service-hpa

echo.
echo PDB:
kubectl get pdb order-service-pdb

echo.
echo Order service reachable at: http://localhost:30081/api/orders
pause
