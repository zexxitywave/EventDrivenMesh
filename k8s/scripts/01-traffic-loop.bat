@echo off
REM ============================================================
REM  01-traffic-loop.bat  (spawned by 01-test-rolling-update.bat)
REM  Sends one request every 500ms, logs any non-2xx response.
REM  Exits when the flag file is deleted by the parent script.
REM ============================================================
SET TOKEN=%1
SET FLAG_FILE=%2
SET LOG_FILE=%3
SET SEQ=0

:loop
if not exist %FLAG_FILE% goto :done

SET /a SEQ+=1

REM Send request, capture HTTP status code only
for /f %%i in ('curl -s -o NUL -w "%%{http_code}" -H "Authorization: Bearer %TOKEN%" http://localhost:30081/api/orders') do SET STATUS=%%i

REM Log anything that is not 200/201/202/204
if "%STATUS%"=="200" goto :ok
if "%STATUS%"=="201" goto :ok
if "%STATUS%"=="202" goto :ok
if "%STATUS%"=="204" goto :ok

echo [SEQ %SEQ%] ERROR - HTTP %STATUS% at %TIME% >> %LOG_FILE%
echo [SEQ %SEQ%] ERROR - HTTP %STATUS% at %TIME%

:ok
timeout /t 0 /nobreak >nul
ping -n 1 -w 500 127.0.0.1 >nul
goto :loop

:done
echo Traffic loop stopped after %SEQ% requests.
