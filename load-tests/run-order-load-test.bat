@echo off
REM ══════════════════════════════════════════════════════════════════════════
REM  Zexxity Order Load Test Runner
REM  Usage: run-order-load-test.bat [stage]
REM  Stages: 1=smoke(100), 2=light(1000), 3=medium(5000), 4=heavy(20000)
REM ══════════════════════════════════════════════════════════════════════════

set JMETER_HOME=C:\apache-jmeter-5.6.3
set JMX=%~dp0order-load-test.jmx
set RESULTS=%~dp0results

REM Default to stage 1 (smoke)
set STAGE=%1
if "%STAGE%"=="" set STAGE=1

if "%STAGE%"=="1" (
    echo [Stage 1] Smoke Test — 10 threads x 10 loops = 100 orders
    set THREADS=10
    set RAMP_UP=5
    set LOOPS=10
)
if "%STAGE%"=="2" (
    echo [Stage 2] Light Load — 50 threads x 20 loops = 1,000 orders
    set THREADS=50
    set RAMP_UP=10
    set LOOPS=20
)
if "%STAGE%"=="3" (
    echo [Stage 3] Medium Load — 100 threads x 50 loops = 5,000 orders
    set THREADS=100
    set RAMP_UP=20
    set LOOPS=50
)
if "%STAGE%"=="4" (
    echo [Stage 4] Heavy Load — 200 threads x 100 loops = 20,000 orders
    set THREADS=200
    set RAMP_UP=30
    set LOOPS=100
)

set TIMESTAMP=%DATE:~-4%%DATE:~3,2%%DATE:~0,2%_%TIME:~0,2%%TIME:~3,2%
set TIMESTAMP=%TIMESTAMP: =0%
set RESULT_FILE=%RESULTS%\stage%STAGE%_%TIMESTAMP%.jtl
set REPORT_DIR=%RESULTS%\report_stage%STAGE%_%TIMESTAMP%

echo JMX:     %JMX%
echo Results: %RESULT_FILE%
echo Report:  %REPORT_DIR%
echo.

%JMETER_HOME%\bin\jmeter.bat -n -t "%JMX%" ^
  -JTHREADS=%THREADS% ^
  -JRAMP_UP=%RAMP_UP% ^
  -JLOOP_COUNT=%LOOPS% ^
  -l "%RESULT_FILE%" ^
  -e -o "%REPORT_DIR%"

echo.
echo ══════════════════════════════════════
echo  Test complete! Open HTML report:
echo  %REPORT_DIR%\index.html
echo ══════════════════════════════════════
pause
