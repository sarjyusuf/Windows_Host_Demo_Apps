@echo off
REM ============================================================================
REM LegacyBridge - Health Check
REM ============================================================================
REM Checks all HTTP health endpoints and reports status.
REM Uses PowerShell Invoke-WebRequest for compatibility with Windows systems
REM that may not have curl installed.
REM ============================================================================

setlocal enabledelayedexpansion

echo.
echo ======================================================================
echo   LegacyBridge - Health Check
echo ======================================================================
echo.

set "PASS=0"
set "FAIL=0"
set "TOTAL=6"

REM ----------------------------------------------------------------------------
REM REST API health (Tomcat - api.war)
REM ----------------------------------------------------------------------------
echo   Checking REST API (http://localhost:8080/api/health)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { Write-Host '       [OK] REST API is healthy (HTTP 200)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM Auth Service health (Tomcat - auth.war)
REM ----------------------------------------------------------------------------
echo   Checking Auth Service (http://localhost:8080/auth/health)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/auth/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { Write-Host '       [OK] Auth Service is healthy (HTTP 200)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM Tika Processor health
REM ----------------------------------------------------------------------------
echo   Checking Tika Processor (http://localhost:8081/health)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8081/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { Write-Host '       [OK] Tika Processor is healthy (HTTP 200)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM Lucene Search health
REM ----------------------------------------------------------------------------
echo   Checking Lucene Search (http://localhost:8082/health)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8082/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { Write-Host '       [OK] Lucene Search is healthy (HTTP 200)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM Document Processor health
REM ----------------------------------------------------------------------------
echo   Checking Document Processor (http://localhost:8083/health)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8083/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { Write-Host '       [OK] Document Processor is healthy (HTTP 200)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM ActiveMQ Web Console
REM ----------------------------------------------------------------------------
echo   Checking ActiveMQ Console (http://localhost:8161/)...
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8161/' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200 -or $r.StatusCode -eq 401) { Write-Host '       [OK] ActiveMQ Console is reachable' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] HTTP ' + $r.StatusCode) -ForegroundColor Red; exit 1 } } catch { if ($_.Exception.Response.StatusCode.value__ -eq 401) { Write-Host '       [OK] ActiveMQ Console is reachable (HTTP 401 - auth required)' -ForegroundColor Green; exit 0 } else { Write-Host ('       [FAIL] ' + $_.Exception.Message) -ForegroundColor Red; exit 1 } }"
if %ERRORLEVEL% EQU 0 ( set /a PASS+=1 ) else ( set /a FAIL+=1 )

REM ----------------------------------------------------------------------------
REM Summary
REM ----------------------------------------------------------------------------
echo.
echo ======================================================================
echo   Health Check Results: %PASS%/%TOTAL% endpoints healthy
echo ======================================================================
echo.

if %FAIL% GTR 0 (
    echo   Some endpoints are not responding. Possible causes:
    echo     - Services have not finished starting (wait and retry)
    echo     - A service failed to start (check logs)
    echo     - Port conflict with another application
    echo     - Firewall blocking localhost connections
    echo.
    echo   Tomcat logs: infrastructure\tomcat\apache-tomcat-*\logs\
    echo   ActiveMQ logs: infrastructure\activemq\apache-activemq-*\data\
    echo.
)

endlocal
