@echo off
REM ============================================================
REM  FlashSale - TWO BUYER LOCKING DEMO
REM
REM  Runs the full TwoBuyersLockingComparisonTest:
REM    1. NO LOCKING        - both buyers confirmed -> OVERSOLD
REM    2. PESSIMISTIC LOCK  - exactly 1 buyer wins
REM
REM  Both timelines print with millisecond timestamps.
REM
REM  Usage:  .\run-two-buyers.cmd
REM ============================================================

echo.
echo ============================================
echo  Running TWO BUYER demo (no-locking + pessimistic)...
echo ============================================
echo.

call "%~dp0mvnw.cmd" test "-Dtest=TwoBuyersLockingComparisonTest"

echo.
echo ============================================
echo  Done. Scroll up for both TIMELINE outputs.
echo ============================================
