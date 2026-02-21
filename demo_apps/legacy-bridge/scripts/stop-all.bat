@echo off
REM ============================================================================
REM LegacyBridge - Stop All Services
REM ============================================================================
REM Stops all services and processes in reverse startup order.
REM ============================================================================

setlocal enabledelayedexpansion

set "SCRIPTS_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPTS_DIR%.."

echo.
echo ======================================================================
echo   LegacyBridge - Stopping All Services
echo ======================================================================
echo.

REM ----------------------------------------------------------------------------
REM 1. Stop Swing Client (kill java process by window title)
REM ----------------------------------------------------------------------------
echo [1/7] Stopping Swing Client...
taskkill /FI "WINDOWTITLE eq SwingClient*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Swing Client stopped.
) else (
    echo       [--] Swing Client was not running.
)

REM ----------------------------------------------------------------------------
REM 2. Stop Batch Runner service
REM ----------------------------------------------------------------------------
echo [2/7] Stopping Batch Runner...
net stop LegacyBridge-BatchRunner >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Batch Runner service stopped.
) else (
    echo       [--] Batch Runner service was not running.
    REM Try killing direct process
    taskkill /FI "WINDOWTITLE eq Batch Runner*" /F >nul 2>&1
)

REM ----------------------------------------------------------------------------
REM 3. Stop Document Processor service
REM ----------------------------------------------------------------------------
echo [3/7] Stopping Document Processor...
net stop LegacyBridge-DocProcessor >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Document Processor service stopped.
) else (
    echo       [--] Document Processor service was not running.
    REM Try killing direct process
    taskkill /FI "WINDOWTITLE eq Document Processor*" /F >nul 2>&1
)

REM ----------------------------------------------------------------------------
REM 4. Stop Lucene Search (kill java process by window title)
REM ----------------------------------------------------------------------------
echo [4/7] Stopping Lucene Search...
taskkill /FI "WINDOWTITLE eq Lucene Search*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Lucene Search stopped.
) else (
    echo       [--] Lucene Search was not running.
)

REM ----------------------------------------------------------------------------
REM 5. Stop Tika Processor (kill java process by window title)
REM ----------------------------------------------------------------------------
echo [5/7] Stopping Tika Processor...
taskkill /FI "WINDOWTITLE eq Tika Processor*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Tika Processor stopped.
) else (
    echo       [--] Tika Processor was not running.
)

REM ----------------------------------------------------------------------------
REM 6. Stop Tomcat service
REM ----------------------------------------------------------------------------
echo [6/7] Stopping Tomcat...
net stop LegacyBridge-Tomcat >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Tomcat service stopped.
) else (
    echo       [--] Tomcat service was not running.
    REM Try killing direct Tomcat process
    taskkill /FI "WINDOWTITLE eq Tomcat*" /F >nul 2>&1
)

REM Wait for Tomcat to fully shut down
timeout /t 5 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 7. Stop ActiveMQ service
REM ----------------------------------------------------------------------------
echo [7/7] Stopping ActiveMQ...
net stop ActiveMQ >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] ActiveMQ service stopped.
) else (
    echo       [--] ActiveMQ service was not running.
    REM Try killing direct ActiveMQ process
    taskkill /FI "WINDOWTITLE eq ActiveMQ*" /F >nul 2>&1
)

REM ----------------------------------------------------------------------------
REM Summary
REM ----------------------------------------------------------------------------
echo.
echo ======================================================================
echo   LegacyBridge - All Services Stopped
echo ======================================================================
echo.
echo   Use start-all.bat to restart all services.
echo.

endlocal
