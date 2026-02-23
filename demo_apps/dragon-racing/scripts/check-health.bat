@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
:: Dragon Racing League - Health Check
:: ============================================================================

color 0F

echo.
echo  [93m============================================================================[0m
echo  [92m         __    __   _______     ___       __      .___________. __    __     [0m
echo  [92m        ^|  ^|  ^|  ^| ^|   ____|   /   \     ^|  ^|     ^|           ^|^|  ^|  ^|  ^|    [0m
echo  [92m        ^|  ^|__^|  ^| ^|  ^|__     /  ^  \    ^|  ^|     `---^|  ^|----`^|  ^|__^|  ^|    [0m
echo  [92m        ^|   __   ^| ^|   __^|   /  /_\  \   ^|  ^|         ^|  ^|     ^|   __   ^|    [0m
echo  [92m        ^|  ^|  ^|  ^| ^|  ^|____ /  _____  \  ^|  `----.    ^|  ^|     ^|  ^|  ^|  ^|    [0m
echo  [92m        ^|__^|  ^|__^| ^|_______/__/     \__\ ^|_______^|    ^|__^|     ^|__^|  ^|__^|    [0m
echo  [93m============================================================================[0m
echo  [97m           DRAGON RACING LEAGUE - SERVICE HEALTH CHECK                       [0m
echo  [93m============================================================================[0m
echo.

set "HEALTHY=0"
set "UNHEALTHY=0"
set "TOTAL=0"

:: ============================================================================
:: SECTION 1: Java Service Health Checks
:: ============================================================================
echo  [96m--- JAVA SERVICES ---[0m
echo.

:: Dragon Stable API (Spring Boot, port 9080)
set /a "TOTAL+=1"
echo   [97m[1/6] Dragon Stable API (Spring Boot - port 9080)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:9080/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '- Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m' $_.Exception.Message }" 2>nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:9080/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: Race Schedule Service (Dropwizard, port 9081 / admin port 9181)
set /a "TOTAL+=1"
echo   [97m[2/6] Race Schedule Service (Dropwizard - port 9081 / admin 9181)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:9081/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '(app port) - Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[93m[WARN][0m App port status:' $response.StatusCode '- trying admin port...' } } catch { Write-Host '[93m[INFO][0m App port unreachable, trying admin port 9181...' }" 2>nul
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:9181/healthcheck' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '(admin port) - Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Admin port status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m Admin port also unreachable:' $_.Exception.Message }" 2>nul
powershell -Command "try { $r1 = Invoke-WebRequest -Uri 'http://localhost:9081/health' -UseBasicParsing -TimeoutSec 3; if ($r1.StatusCode -eq 200) { exit 0 } } catch {} ; try { $r2 = Invoke-WebRequest -Uri 'http://localhost:9181/healthcheck' -UseBasicParsing -TimeoutSec 3; if ($r2.StatusCode -eq 200) { exit 0 } } catch {} ; exit 1" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: Leaderboard Service (Micronaut, port 9082)
set /a "TOTAL+=1"
echo   [97m[3/6] Leaderboard Service (Micronaut - port 9082)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:9082/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '- Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m' $_.Exception.Message }" 2>nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:9082/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: Media Service (Jetty, port 9083)
set /a "TOTAL+=1"
echo   [97m[4/6] Media Service (Jetty - port 9083)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:9083/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '- Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m' $_.Exception.Message }" 2>nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:9083/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: ============================================================================
:: SECTION 2: .NET Service Health Checks
:: ============================================================================
echo  [96m--- .NET SERVICES ---[0m
echo.

:: Race Portal (port 5000)
set /a "TOTAL+=1"
echo   [97m[5/6] Race Portal (.NET - port 5000)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:5000/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '- Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m' $_.Exception.Message }" 2>nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:5000/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: Betting API (port 5001)
set /a "TOTAL+=1"
echo   [97m[6/6] Betting API (.NET - port 5001)[0m
<nul set /p "=        "
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:5001/health' -UseBasicParsing -TimeoutSec 5; if ($response.StatusCode -eq 200) { Write-Host '[92m[HEALTHY][0m Status:' $response.StatusCode '- Content:' ($response.Content.Substring(0, [Math]::Min(80, $response.Content.Length))) } else { Write-Host '[91m[UNHEALTHY][0m Status:' $response.StatusCode } } catch { Write-Host '[91m[DOWN][0m' $_.Exception.Message }" 2>nul
powershell -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:5001/health' -UseBasicParsing -TimeoutSec 5; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if !errorlevel! equ 0 ( set /a "HEALTHY+=1" ) else ( set /a "UNHEALTHY+=1" )
echo.

:: ============================================================================
:: SECTION 3: Process List
:: ============================================================================
echo  [96m--- RUNNING PROCESSES ---[0m
echo.

echo   [93mJava Processes:[0m
powershell -Command "Get-WmiObject Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -match 'dragon-racing' } | ForEach-Object { Write-Host ('    PID: {0,-8} CMD: {1}' -f $_.ProcessId, $_.CommandLine.Substring(0, [Math]::Min(100, $_.CommandLine.Length))) }" 2>nul
powershell -Command "$procs = Get-WmiObject Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -match 'dragon-racing' }; if (-not $procs) { Write-Host '    [93m(none found)[0m' }" 2>nul
echo.

echo   [95m.NET Processes:[0m
powershell -Command "Get-WmiObject Win32_Process -Filter \"name='dotnet.exe'\" | Where-Object { $_.CommandLine -match 'DragonRacing' } | ForEach-Object { Write-Host ('    PID: {0,-8} CMD: {1}' -f $_.ProcessId, $_.CommandLine.Substring(0, [Math]::Min(100, $_.CommandLine.Length))) }" 2>nul
powershell -Command "$procs = Get-WmiObject Win32_Process -Filter \"name='dotnet.exe'\" | Where-Object { $_.CommandLine -match 'DragonRacing' }; if (-not $procs) { Write-Host '    [93m(none found)[0m' }" 2>nul
echo.

:: ============================================================================
:: SECTION 4: Listening Ports
:: ============================================================================
echo  [96m--- LISTENING PORTS (Dragon Racing) ---[0m
echo.

echo   [97m  Checking ports: 9080, 9081, 9082, 9083, 9181, 5000, 5001[0m
echo.

for %%P in (9080 9081 9082 9083 9181 5000 5001) do (
    <nul set /p "=    Port %%P: "
    powershell -Command "$conn = Get-NetTCPConnection -LocalPort %%P -State Listen -ErrorAction SilentlyContinue; if ($conn) { $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue; Write-Host '[92mLISTENING[0m -' $proc.ProcessName '(PID:' $conn.OwningProcess ')' } else { Write-Host '[91mNOT LISTENING[0m' }" 2>nul
)
echo.

:: ============================================================================
:: SUMMARY
:: ============================================================================
echo  [93m============================================================================[0m
echo  [97m  HEALTH CHECK SUMMARY[0m
echo  [93m============================================================================[0m
echo.

if !UNHEALTHY! equ 0 (
    echo   [92m  STATUS: ALL SYSTEMS OPERATIONAL[0m
    echo.
    echo   [92m         /\_/\[0m
    echo   [92m        ( o.o )   All !HEALTHY!/!TOTAL! services are healthy![0m
    echo   [92m         ^> ^ ^<    The dragons are ready to race![0m
) else if !HEALTHY! gtr 0 (
    echo   [93m  STATUS: PARTIAL - SOME SERVICES DOWN[0m
    echo.
    echo   [93m         /\_/\[0m
    echo   [93m        ( o.~ )   !HEALTHY!/!TOTAL! services healthy, !UNHEALTHY! down[0m
    echo   [93m         ^> ^ ^<    Some dragons need attention![0m
) else (
    echo   [91m  STATUS: ALL SERVICES DOWN[0m
    echo.
    echo   [91m         /\_/\[0m
    echo   [91m        ( x.x )   0/!TOTAL! services responding[0m
    echo   [91m         ^> ^ ^<    The dragons are sleeping! Run start-all.bat[0m
)
echo.
echo   [97m  Healthy:   [92m!HEALTHY![0m
echo   [97m  Unhealthy: [91m!UNHEALTHY![0m
echo   [97m  Total:     !TOTAL![0m
echo.
echo  [93m============================================================================[0m
echo.

endlocal
exit /b 0
