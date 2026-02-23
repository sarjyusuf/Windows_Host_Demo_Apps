@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
:: Dragon Racing League - Stop All Services
:: ============================================================================

color 0F

echo.
echo  [93m============================================================================[0m
echo  [91m        _______ _________ _______  _______                                  [0m
echo  [91m       (  ____ \\__   __/(  ___  )(  ____ )                                 [0m
echo  [91m       ^| (    \/   ) (   ^| (   ) ^|^| (    )^|                                [0m
echo  [91m       ^| (_____    ^| ^|   ^| ^|   ^| ^|^| (____)\^|                                [0m
echo  [91m       (_____  )   ^| ^|   ^| ^|   ^| ^|^|  _____)                                 [0m
echo  [91m             ) ^|   ^| ^|   ^| ^|   ^| ^|^| (                                       [0m
echo  [91m       /\____) ^|   ^| ^|   ^| (___) ^|^| )                                       [0m
echo  [91m       \_______)   )_(   (_______)\^|/                                        [0m
echo  [93m============================================================================[0m
echo  [97m         STOPPING ALL DRAGON RACING LEAGUE SERVICES                          [0m
echo  [93m============================================================================[0m
echo.

set "JAVA_KILLED=0"
set "DOTNET_KILLED=0"
set "ERRORS=0"

:: ============================================================================
:: STEP 1: Stop Java services
:: ============================================================================
echo [96m[STEP 1/3] Stopping Java services (dragon-racing)...[0m
echo.

:: Use WMIC to find and terminate java.exe processes with dragon-racing in command line
echo   [97m    Searching for Java processes matching "dragon-racing"...[0m
echo.

:: First, list what we're about to kill
for /f "tokens=1,2 delims=," %%a in ('wmic process where "name='java.exe' and commandline like '%%dragon-racing%%'" get processid^,commandline /format:csv 2^>nul ^| findstr /i "dragon-racing"') do (
    echo   [93m    Found: PID %%b[0m
)

:: Now terminate them
wmic process where "name='java.exe' and commandline like '%%dragon-racing%%'" call terminate >nul 2>&1
if !errorlevel! equ 0 (
    echo   [92m    [OK][0m Terminated Java dragon-racing processes via WMIC
    set "JAVA_KILLED=1"
) else (
    echo   [93m    [INFO][0m No Java dragon-racing processes found ^(may already be stopped^)
)

:: Fallback: use taskkill as a secondary method
echo.
echo   [97m    Running taskkill fallback for any remaining Java processes...[0m
for %%J in (dragon-stable race-schedule leaderboard media-service sync-scheduler) do (
    taskkill /f /fi "IMAGENAME eq java.exe" /fi "WINDOWTITLE eq %%J*" >nul 2>&1
)

:: Also try to kill by checking command line with PowerShell for thoroughness
powershell -Command "Get-WmiObject Win32_Process -Filter \"name='java.exe'\" | Where-Object { $_.CommandLine -match 'dragon-racing' } | ForEach-Object { Write-Host '  [92m    [KILL][0m Terminating java.exe PID:' $_.ProcessId; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" 2>nul

echo.

:: ============================================================================
:: STEP 2: Stop .NET services
:: ============================================================================
echo [96m[STEP 2/3] Stopping .NET services (DragonRacing)...[0m
echo.

echo   [97m    Searching for dotnet.exe processes matching "DragonRacing"...[0m
echo.

:: Use WMIC to find and terminate dotnet.exe processes with DragonRacing in command line
for /f "tokens=1,2 delims=," %%a in ('wmic process where "name='dotnet.exe' and commandline like '%%DragonRacing%%'" get processid^,commandline /format:csv 2^>nul ^| findstr /i "DragonRacing"') do (
    echo   [93m    Found: PID %%b[0m
)

wmic process where "name='dotnet.exe' and commandline like '%%DragonRacing%%'" call terminate >nul 2>&1
if !errorlevel! equ 0 (
    echo   [92m    [OK][0m Terminated .NET DragonRacing processes via WMIC
    set "DOTNET_KILLED=1"
) else (
    echo   [93m    [INFO][0m No .NET DragonRacing processes found ^(may already be stopped^)
)

:: Fallback: use PowerShell for thoroughness
echo.
echo   [97m    Running PowerShell fallback for any remaining .NET processes...[0m
powershell -Command "Get-WmiObject Win32_Process -Filter \"name='dotnet.exe'\" | Where-Object { $_.CommandLine -match 'DragonRacing' } | ForEach-Object { Write-Host '  [92m    [KILL][0m Terminating dotnet.exe PID:' $_.ProcessId; Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }" 2>nul

echo.

:: ============================================================================
:: STEP 3: Verification
:: ============================================================================
echo [96m[STEP 3/3] Verifying all processes are stopped...[0m
echo.

set "REMAINING_JAVA=0"
set "REMAINING_DOTNET=0"

:: Check for remaining Java processes
for /f %%c in ('wmic process where "name='java.exe' and commandline like '%%dragon-racing%%'" get processid 2^>nul ^| find /c /v ""') do (
    set /a "REMAINING_JAVA=%%c - 1"
)

:: Check for remaining .NET processes
for /f %%c in ('wmic process where "name='dotnet.exe' and commandline like '%%DragonRacing%%'" get processid 2^>nul ^| find /c /v ""') do (
    set /a "REMAINING_DOTNET=%%c - 1"
)

if !REMAINING_JAVA! leq 0 if !REMAINING_DOTNET! leq 0 (
    echo   [92m    [OK] All Dragon Racing processes have been stopped successfully.[0m
) else (
    if !REMAINING_JAVA! gtr 0 (
        echo   [91m    [WARN] !REMAINING_JAVA! Java process(es) still running.[0m
    )
    if !REMAINING_DOTNET! gtr 0 (
        echo   [91m    [WARN] !REMAINING_DOTNET! .NET process(es) still running.[0m
    )
    echo.
    echo   [91m    You may need to manually kill these processes using Task Manager.[0m
)

echo.
echo  [93m============================================================================[0m
echo  [97m  DRAGON RACING LEAGUE - SHUTDOWN COMPLETE                                  [0m
echo  [93m============================================================================[0m
echo.
echo  [97m  Services targeted for termination:[0m
echo.
echo  [92m  JAVA SERVICES:[0m
echo  [97m    [x] Dragon Stable API      ^(dragon-stable.jar^)[0m
echo  [97m    [x] Race Schedule Service   ^(race-schedule.jar^)[0m
echo  [97m    [x] Leaderboard Service     ^(leaderboard.jar^)[0m
echo  [97m    [x] Media Service           ^(media-service.jar^)[0m
echo  [97m    [x] Training Scheduler      ^(sync-scheduler.jar^)[0m
echo.
echo  [95m  .NET SERVICES:[0m
echo  [97m    [x] Race Portal             ^(DragonRacing.Portal.dll^)[0m
echo  [97m    [x] Betting API             ^(DragonRacing.BettingApi.dll^)[0m
echo  [97m    [x] Race Simulator          ^(DragonRacing.RaceSimulator.dll^)[0m
echo  [97m    [x] Payout Processor        ^(DragonRacing.PayoutProcessor.dll^)[0m
echo.
echo  [91m       ~~ The dragons rest... for now. ~~[0m
echo.
echo  [93m============================================================================[0m
echo.

endlocal
exit /b 0
