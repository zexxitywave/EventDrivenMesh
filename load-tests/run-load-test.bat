@echo off
REM ============================================================
REM  Ecommerce Microservices - JMeter Load Test Runner
REM  Prerequisites:
REM    1. JMeter 5.6+ installed and JMETER_HOME set, OR jmeter.bat on PATH
REM    2. All services running (docker-compose up -d, then start each service)
REM    3. A test user already registered + email verified in auth-service
REM       Email:    loadtest@zexxity.online
REM       Password: LoadTest@123
REM ============================================================

SET JMETER_HOME=C:\apache-jmeter-5.6.3
SET JMX_FILE=ecommerce-load-test.jmx
SET RESULTS_DIR=results
SET REPORT_DIR=results\html-report
SET TIMESTAMP=%DATE:~-4,4%%DATE:~-10,2%%DATE:~-7,2%_%TIME:~0,2%%TIME:~3,2%
SET TIMESTAMP=%TIMESTAMP: =0%

REM Create results directory
if not exist %RESULTS_DIR% mkdir %RESULTS_DIR%
if not exist %REPORT_DIR% mkdir %REPORT_DIR%

echo.
echo ============================================================
echo  Starting Load Test: %JMX_FILE%
echo  Threads : 50 (configurable via THREADS variable in JMX)
echo  Ramp-up : 30 seconds
echo  Loops   : 10 per thread
echo  Target  : http://localhost:8080 (API Gateway)
echo ============================================================
echo.

REM Run JMeter in non-GUI mode
%JMETER_HOME%\bin\jmeter.bat ^
  -n ^
  -t %JMX_FILE% ^
  -l %RESULTS_DIR%\results-%TIMESTAMP%.jtl ^
  -e ^
  -o %REPORT_DIR% ^
  -Jjmeter.reportgenerator.overall_granularity=1000

echo.
echo ============================================================
echo  Test complete!
echo  Results JTL : %RESULTS_DIR%\results-%TIMESTAMP%.jtl
echo  HTML Report : %REPORT_DIR%\index.html
echo  Open the HTML report in your browser for full metrics.
echo ============================================================
pause
