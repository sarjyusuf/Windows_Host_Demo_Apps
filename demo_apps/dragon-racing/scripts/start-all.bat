@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
:: Dragon Racing League - Start All Services
:: ============================================================================

:: Set console colors - bright white on dark red for the header
color 0F

echo.
echo  [93m============================================================================[0m
echo  [93m     ___                                                                    [0m
echo  [91m    /   \____  _____ _____ ____  _ __                                       [0m
echo  [91m   / /\ / __ \/ __  / __  / __ \/ '_ \                                      [0m
echo  [91m  / /_// / / / /_/ / /_/ / /_/ / / / /                                      [0m
echo  [91m /___,'_/ /_/\__,_/\__, /\____/_/ /_/                                       [0m
echo  [91m                  /____/                                                     [0m
echo  [93m     ____             _               _                                      [0m
echo  [31m    |  _ \ __ _  ___(_)_ __   __ _  | |    ___  __ _  __ _ _   _  ___       [0m
echo  [31m    | |_) / _` |/ __| | '_ \ / _` | | |   / _ \/ _` |/ _` | | | |/ _ \     [0m
echo  [31m    |  _ ^< (_| | (__| | | | | (_| | | |__|  __/ (_| | (_| | |_| |  __/     [0m
echo  [31m    |_| \_\__,_|\___|_|_| |_|\__, | |_____\___|\__,_|\__, |\__,_|\___|      [0m
echo  [31m                             |___/                    |___/                   [0m
echo  [93m============================================================================[0m
echo  [97m              STARTING ALL DRAGON RACING SERVICES                            [0m
echo  [93m============================================================================[0m
echo.

:: ============================================================================
:: STEP 1: Create data directories
:: ============================================================================
echo [96m[STEP 1/6] Creating data directories...[0m
echo.

set "DIRS=C:\dragon-racing\data C:\dragon-racing\data\media C:\dragon-racing\queues\races\pending C:\dragon-racing\queues\races\completed C:\dragon-racing\queues\payouts\pending C:\dragon-racing\queues\payouts\completed C:\dragon-racing\logs"

for %%D in (%DIRS%) do (
    if not exist "%%D" (
        mkdir "%%D" 2>nul
        if !errorlevel! equ 0 (
            echo   [92m[OK][0m Created: %%D
        ) else (
            echo   [91m[FAIL][0m Could not create: %%D
        )
    ) else (
        echo   [93m[SKIP][0m Already exists: %%D
    )
)
echo.

:: ============================================================================
:: STEP 2: Set JAVA_HOME
:: ============================================================================
echo [96m[STEP 2/6] Configuring Java environment...[0m
echo.

set "JAVA_HOME=C:\Java\jdk17\jdk17.0.18_9"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if exist "%JAVA_HOME%\bin\java.exe" (
    echo   [92m[OK][0m JAVA_HOME set to %JAVA_HOME%
    for /f "tokens=*" %%v in ('"%JAVA_HOME%\bin\java.exe" -version 2^>^&1') do (
        echo   [97m       %%v[0m
        goto :java_version_done
    )
    :java_version_done
) else (
    echo   [91m[WARN][0m JAVA_HOME directory not found: %JAVA_HOME%
    echo   [91m       Java services may fail to start.[0m
)
echo.

:: ============================================================================
:: STEP 3: Start Java services (data providers)
:: ============================================================================
echo [96m[STEP 3/6] Starting Java services (data providers)...[0m
echo.
echo   [93m~^~ Dragon Stable API ~^~[0m
echo   [97m    Framework: Spring Boot  ^|  Port: 9080[0m

set "JAVA_DIR=C:\dragon-racing\java"
set "LOG_DIR=C:\dragon-racing\logs"

:: Dragon Stable API (Spring Boot, port 9080)
set "DD_SERVICE=dragonracing-dragon-stable"
echo   [97m    Starting dragon-stable.jar...[0m
start "" /b "%JAVA_HOME%\bin\java.exe" -jar "%JAVA_DIR%\dragon-stable.jar" > "%LOG_DIR%\dragon-stable-stdout.log" 2> "%LOG_DIR%\dragon-stable-stderr.log"
echo   [92m    [LAUNCHED][0m Dragon Stable API ^(port 9080^)
echo.

:: Race Schedule Service (Dropwizard, port 9081)
set "DD_SERVICE=dragonracing-race-schedule"
echo   [93m~^~ Race Schedule Service ~^~[0m
echo   [97m    Framework: Dropwizard  ^|  Port: 9081[0m
echo   [97m    Starting race-schedule.jar...[0m
start "" /b "%JAVA_HOME%\bin\java.exe" -jar "%JAVA_DIR%\race-schedule.jar" server "%JAVA_DIR%\config.yml" > "%LOG_DIR%\race-schedule-stdout.log" 2> "%LOG_DIR%\race-schedule-stderr.log"
echo   [92m    [LAUNCHED][0m Race Schedule Service ^(port 9081^)
echo.

:: Leaderboard Service (Micronaut, port 9082)
set "DD_SERVICE=dragonracing-leaderboard"
echo   [93m~^~ Leaderboard Service ~^~[0m
echo   [97m    Framework: Micronaut  ^|  Port: 9082[0m
echo   [97m    Starting leaderboard.jar...[0m
start "" /b "%JAVA_HOME%\bin\java.exe" -jar "%JAVA_DIR%\leaderboard.jar" > "%LOG_DIR%\leaderboard-stdout.log" 2> "%LOG_DIR%\leaderboard-stderr.log"
echo   [92m    [LAUNCHED][0m Leaderboard Service ^(port 9082^)
echo.

:: Media Service (Jetty, port 9083)
set "DD_SERVICE=dragonracing-media-service"
echo   [93m~^~ Media Service ~^~[0m
echo   [97m    Framework: Jetty  ^|  Port: 9083[0m
echo   [97m    Starting media-service.jar...[0m
start "" /b "%JAVA_HOME%\bin\java.exe" -jar "%JAVA_DIR%\media-service.jar" > "%LOG_DIR%\media-service-stdout.log" 2> "%LOG_DIR%\media-service-stderr.log"
echo   [92m    [LAUNCHED][0m Media Service ^(port 9083^)
echo.

:: Training Scheduler (Quartz)
set "DD_SERVICE=dragonracing-sync-scheduler"
echo   [93m~^~ Training Scheduler ~^~[0m
echo   [97m    Framework: Quartz  ^|  Background Process[0m
echo   [97m    Starting sync-scheduler.jar...[0m
start "" /b "%JAVA_HOME%\bin\java.exe" -jar "%JAVA_DIR%\sync-scheduler.jar" > "%LOG_DIR%\sync-scheduler-stdout.log" 2> "%LOG_DIR%\sync-scheduler-stderr.log"
echo   [92m    [LAUNCHED][0m Training Scheduler
echo.

echo   [92m    All 5 Java services launched.[0m
echo.

:: ============================================================================
:: STEP 4: Wait for Java services to initialize
:: ============================================================================
echo [96m[STEP 4/6] Waiting 15 seconds for Java services to initialize...[0m
echo.
echo   [97m    Java services need time to warm up their dragon-fire engines...[0m
echo.

for /l %%i in (15,-1,1) do (
    <nul set /p "=  [93m    Countdown: %%i seconds remaining...  [0m"
    echo.
    timeout /t 1 /nobreak >nul
)
echo.
echo   [92m    Java warm-up complete![0m
echo.

:: ============================================================================
:: STEP 5: Start .NET services
:: ============================================================================
echo [96m[STEP 5/6] Starting .NET services...[0m
echo.

set "DOTNET_DIR=C:\dragon-racing\dotnet"

:: Race Portal (port 5000)
set "DD_SERVICE=dragonracing-portal"
echo   [95m~^~ Race Portal ~^~[0m
echo   [97m    Runtime: .NET  ^|  Port: 5000[0m
echo   [97m    Starting DragonRacing.Portal.dll...[0m
start "" /b dotnet "%DOTNET_DIR%\portal\publish\DragonRacing.Portal.dll" > "%LOG_DIR%\race-portal-stdout.log" 2> "%LOG_DIR%\race-portal-stderr.log"
echo   [92m    [LAUNCHED][0m Race Portal ^(port 5000^)
echo.

:: Betting API (port 5001)
set "DD_SERVICE=dragonracing-betting-api"
echo   [95m~^~ Betting API ~^~[0m
echo   [97m    Runtime: .NET  ^|  Port: 5001[0m
echo   [97m    Starting DragonRacing.BettingApi.dll...[0m
start "" /b dotnet "%DOTNET_DIR%\betting-api\publish\DragonRacing.BettingApi.dll" > "%LOG_DIR%\betting-api-stdout.log" 2> "%LOG_DIR%\betting-api-stderr.log"
echo   [92m    [LAUNCHED][0m Betting API ^(port 5001^)
echo.

:: Race Simulator
set "DD_SERVICE=dragonracing-race-simulator"
echo   [95m~^~ Race Simulator ~^~[0m
echo   [97m    Runtime: .NET  ^|  Background Process[0m
echo   [97m    Starting DragonRacing.RaceSimulator.dll...[0m
start "" /b dotnet "%DOTNET_DIR%\race-simulator\publish\DragonRacing.RaceSimulator.dll" > "%LOG_DIR%\race-simulator-stdout.log" 2> "%LOG_DIR%\race-simulator-stderr.log"
echo   [92m    [LAUNCHED][0m Race Simulator
echo.

:: Payout Processor
set "DD_SERVICE=dragonracing-payout-processor"
echo   [95m~^~ Payout Processor ~^~[0m
echo   [97m    Runtime: .NET  ^|  Background Process[0m
echo   [97m    Starting DragonRacing.PayoutProcessor.dll...[0m
start "" /b dotnet "%DOTNET_DIR%\payout-processor\publish\DragonRacing.PayoutProcessor.dll" > "%LOG_DIR%\payout-processor-stdout.log" 2> "%LOG_DIR%\payout-processor-stderr.log"
echo   [92m    [LAUNCHED][0m Payout Processor
echo.

echo   [92m    All 4 .NET services launched.[0m
echo.

:: ============================================================================
:: STEP 6: Summary
:: ============================================================================
echo [96m[STEP 6/6] Startup Summary[0m
echo.
echo  [93m============================================================================[0m
echo  [97m  DRAGON RACING LEAGUE - ALL SERVICES LAUNCHED                              [0m
echo  [93m============================================================================[0m
echo.
echo  [92m  JAVA SERVICES (5):[0m
echo  [97m    [1] Dragon Stable API      (Spring Boot)   http://localhost:9080[0m
echo  [97m    [2] Race Schedule Service   (Dropwizard)    http://localhost:9081[0m
echo  [97m    [3] Leaderboard Service     (Micronaut)     http://localhost:9082[0m
echo  [97m    [4] Media Service           (Jetty)         http://localhost:9083[0m
echo  [97m    [5] Training Scheduler      (Quartz)        background[0m
echo.
echo  [95m  .NET SERVICES (4):[0m
echo  [97m    [6] Race Portal                             http://localhost:5000[0m
echo  [97m    [7] Betting API                             http://localhost:5001[0m
echo  [97m    [8] Race Simulator                          background[0m
echo  [97m    [9] Payout Processor                        background[0m
echo.
echo  [93m  LOG FILES:[0m
echo  [97m    All logs written to: C:\dragon-racing\logs\[0m
echo.
echo  [93m  NEXT STEPS:[0m
echo  [97m    Run [92mcheck-health.bat[97m to verify all services are healthy[0m
echo  [97m    Run [92mtrigger-race.bat[97m to start a dragon race![0m
echo.
echo  [93m============================================================================[0m
echo  [91m           ~~ May the fiercest dragon win! ~~[0m
echo  [93m============================================================================[0m
echo.

endlocal
exit /b 0
