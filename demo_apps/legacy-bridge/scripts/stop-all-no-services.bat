@echo off
REM ============================================================================
REM LegacyBridge - Stop All (No Services Mode)
REM ============================================================================
REM Kills all java processes that were started by start-all-no-services.bat.
REM Uses taskkill to terminate processes by window title and name.
REM ============================================================================

setlocal enabledelayedexpansion

set "SCRIPTS_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPTS_DIR%.."

echo.
echo ======================================================================
echo   LegacyBridge - Stopping All Processes (No Services Mode)
echo ======================================================================
echo.

REM ----------------------------------------------------------------------------
REM Stop each process by window title (reverse order of startup)
REM ----------------------------------------------------------------------------

echo [1/7] Stopping Swing Client...
taskkill /FI "WINDOWTITLE eq SwingClient*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Swing Client terminated.
) else (
    echo       [--] Swing Client was not running.
)

echo [2/7] Stopping Batch Runner...
taskkill /FI "WINDOWTITLE eq Batch Runner*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Batch Runner terminated.
) else (
    echo       [--] Batch Runner was not running.
)

echo [3/7] Stopping Document Processor...
taskkill /FI "WINDOWTITLE eq Document Processor*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Document Processor terminated.
) else (
    echo       [--] Document Processor was not running.
)

echo [4/7] Stopping Lucene Search...
taskkill /FI "WINDOWTITLE eq Lucene Search*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Lucene Search terminated.
) else (
    echo       [--] Lucene Search was not running.
)

echo [5/7] Stopping Tika Processor...
taskkill /FI "WINDOWTITLE eq Tika Processor*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Tika Processor terminated.
) else (
    echo       [--] Tika Processor was not running.
)

echo [6/7] Stopping Tomcat...
taskkill /FI "WINDOWTITLE eq Tomcat*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Tomcat terminated.
) else (
    echo       [--] Tomcat was not running.
)

echo [7/7] Stopping ActiveMQ...
taskkill /FI "WINDOWTITLE eq ActiveMQ*" /F >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] ActiveMQ terminated.
) else (
    echo       [--] ActiveMQ was not running.
)

REM ----------------------------------------------------------------------------
REM Cleanup: Kill any remaining java.exe processes from LegacyBridge
REM (Only if there are orphaned java processes listening on our ports)
REM ----------------------------------------------------------------------------
echo.
echo Checking for orphaned java processes on LegacyBridge ports...

REM Check port 8080 (Tomcat)
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":8080" ^| findstr "LISTENING" 2^>nul') do (
    echo       Found process %%P on port 8080, killing...
    taskkill /PID %%P /F >nul 2>&1
)

REM Check port 8081 (Tika)
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":8081" ^| findstr "LISTENING" 2^>nul') do (
    echo       Found process %%P on port 8081, killing...
    taskkill /PID %%P /F >nul 2>&1
)

REM Check port 8082 (Lucene)
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":8082" ^| findstr "LISTENING" 2^>nul') do (
    echo       Found process %%P on port 8082, killing...
    taskkill /PID %%P /F >nul 2>&1
)

REM Check port 8083 (Document Processor)
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":8083" ^| findstr "LISTENING" 2^>nul') do (
    echo       Found process %%P on port 8083, killing...
    taskkill /PID %%P /F >nul 2>&1
)

REM Check port 61616 (ActiveMQ)
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":61616" ^| findstr "LISTENING" 2^>nul') do (
    echo       Found process %%P on port 61616, killing...
    taskkill /PID %%P /F >nul 2>&1
)

echo.
echo ======================================================================
echo   LegacyBridge - All Processes Stopped
echo ======================================================================
echo.
echo   Use start-all-no-services.bat to restart all processes.
echo.

endlocal
