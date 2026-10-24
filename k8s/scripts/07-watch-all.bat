@echo off
REM ============================================================
REM  07-watch-all.bat
REM  Live dashboard — run this in a separate terminal window
REM  while executing any of the other test scripts.
REM  Refreshes every 5 seconds showing full cluster state.
REM ============================================================

:loop
cls
echo ════════════════════════════════════════════════════════════
echo  ORDER SERVICE - LIVE K8S DASHBOARD   %DATE% %TIME%
echo ════════════════════════════════════════════════════════════
echo.
echo ── PODS ─────────────────────────────────────────────────────
kubectl get pods -l app=order-service -o wide
echo.
echo ── HPA ──────────────────────────────────────────────────────
kubectl get hpa order-service-hpa
echo.
echo ── PDB ──────────────────────────────────────────────────────
kubectl get pdb order-service-pdb
echo.
echo ── SERVICES ─────────────────────────────────────────────────
kubectl get svc -l app=order-service
echo.
echo ── DEPLOYMENT ───────────────────────────────────────────────
kubectl get deployment order-service
echo.
echo ── RECENT EVENTS ────────────────────────────────────────────
kubectl get events --field-selector involvedObject.name=order-service ^
  --sort-by=.lastTimestamp 2>nul | tail -5
echo.
echo ── LIVE RESOURCE USAGE (needs metrics-server) ───────────────
kubectl top pods -l app=order-service 2>nul
echo.
echo ════════════════════════════════════════════════════════════
echo  Refreshing in 5s... (Ctrl+C to stop)
echo ════════════════════════════════════════════════════════════
timeout /t 5 /nobreak >nul
goto :loop
