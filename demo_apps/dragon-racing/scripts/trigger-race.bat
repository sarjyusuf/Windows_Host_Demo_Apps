@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
:: Dragon Racing League - Trigger Race
:: ============================================================================

color 0F

echo.
echo  [93m============================================================================[0m
echo  [91m                       .     .                                               [0m
echo  [91m                      / \   / \                                              [0m
echo  [91m                     /   \_/   \        [93m~ DRAGON RACING LEAGUE ~[91m             [0m
echo  [91m              ______/           \______                                       [0m
echo  [91m             /  [93mo[91m                 [93mo[91m  \     [97mRACE TRIGGER SYSTEM[91m                [0m
echo  [91m            ^|     ___________     ^|                                           [0m
echo  [91m             \   /  ^|^|^|^|^|^|^|  \   /     [93m"Where dragons dare to fly[91m            [0m
echo  [91m              \_/   ^|^|^|^|^|^|^|   \_/      [93m and fortunes change in a[91m             [0m
echo  [91m               ^|   ^|^|^|^|^|^|^|   ^|        [93m heartbeat!"[91m                          [0m
echo  [91m              /^|^|^|\       /^|^|^|\                                                [0m
echo  [91m             / ^|^|^| \     / ^|^|^| \                                               [0m
echo  [91m            /  ^|^|^|  \   /  ^|^|^|  \                                              [0m
echo  [93m============================================================================[0m
echo.

:: ============================================================================
:: STEP 1: Get the next scheduled race
:: ============================================================================
echo  [96m[STEP 1/5] Fetching the next scheduled race from Race Schedule Service...[0m
echo.

set "RACE_ID="
set "RACE_NAME="

:: Call the Race Schedule Service to get the first scheduled race
powershell -Command ^
    "try {" ^
    "  $response = Invoke-WebRequest -Uri 'http://localhost:9081/api/races/scheduled' -UseBasicParsing -TimeoutSec 10;" ^
    "  $races = $response.Content | ConvertFrom-Json;" ^
    "  if ($races -and $races.Count -gt 0) {" ^
    "    $race = $races[0];" ^
    "    Write-Host '  [92m[OK][0m Found scheduled race!';" ^
    "    Write-Host '';" ^
    "    Write-Host '  [93m  ================================================[0m';" ^
    "    Write-Host ('  [97m  Race ID:       {0}[0m' -f $race.id);" ^
    "    Write-Host ('  [97m  Race Name:     {0}[0m' -f $race.name);" ^
    "    Write-Host ('  [97m  Track:         {0}[0m' -f $race.track);" ^
    "    Write-Host ('  [97m  Distance:      {0}[0m' -f $race.distance);" ^
    "    Write-Host ('  [97m  Participants:  {0}[0m' -f $race.participantCount);" ^
    "    Write-Host ('  [97m  Scheduled:     {0}[0m' -f $race.scheduledTime);" ^
    "    Write-Host '  [93m  ================================================[0m';" ^
    "    $race.id | Out-File -FilePath '%TEMP%\dragon-race-id.txt' -NoNewline;" ^
    "    $race.name | Out-File -FilePath '%TEMP%\dragon-race-name.txt' -NoNewline;" ^
    "    exit 0;" ^
    "  } else {" ^
    "    Write-Host '  [91m[WARN][0m No scheduled races found.';" ^
    "    Write-Host '  [97m         The Race Schedule Service returned an empty list.[0m';" ^
    "    Write-Host '  [97m         Make sure races have been configured.[0m';" ^
    "    exit 1;" ^
    "  }" ^
    "} catch {" ^
    "  Write-Host ('  [91m[ERROR][0m Could not reach Race Schedule Service: ' + $_.Exception.Message);" ^
    "  Write-Host '  [97m          Is the Race Schedule Service running on port 9081?[0m';" ^
    "  Write-Host '  [97m          Run check-health.bat to verify.[0m';" ^
    "  exit 1;" ^
    "}"

if !errorlevel! neq 0 (
    echo.
    echo  [91m  Cannot proceed without a scheduled race. Aborting.[0m
    echo.
    goto :race_failed
)

:: Read the race ID from temp file
if exist "%TEMP%\dragon-race-id.txt" (
    set /p RACE_ID=<"%TEMP%\dragon-race-id.txt"
    del "%TEMP%\dragon-race-id.txt" >nul 2>&1
)
if exist "%TEMP%\dragon-race-name.txt" (
    set /p RACE_NAME=<"%TEMP%\dragon-race-name.txt"
    del "%TEMP%\dragon-race-name.txt" >nul 2>&1
)

echo.

:: ============================================================================
:: STEP 2: Confirm and start the race
:: ============================================================================
echo  [96m[STEP 2/5] Starting the race...[0m
echo.

echo   [93m  Igniting dragon engines...[0m
echo   [91m  >>>  FIRE  <<<[0m
echo.

:: POST to start the race
powershell -Command ^
    "try {" ^
    "  $body = @{ raceId = '%RACE_ID%'; action = 'start' } | ConvertTo-Json;" ^
    "  $response = Invoke-WebRequest -Uri 'http://localhost:9081/api/races/%RACE_ID%/start' -Method POST -Body $body -ContentType 'application/json' -UseBasicParsing -TimeoutSec 10;" ^
    "  if ($response.StatusCode -eq 200 -or $response.StatusCode -eq 202) {" ^
    "    Write-Host '  [92m[OK][0m Race start command accepted! Status:' $response.StatusCode;" ^
    "    exit 0;" ^
    "  } else {" ^
    "    Write-Host '  [93m[WARN][0m Unexpected status:' $response.StatusCode;" ^
    "    Write-Host '  [97m        Response:' $response.Content;" ^
    "    exit 0;" ^
    "  }" ^
    "} catch {" ^
    "  Write-Host ('  [91m[ERROR][0m Failed to start race: ' + $_.Exception.Message);" ^
    "  exit 1;" ^
    "}"

echo.

:: ============================================================================
:: STEP 3: The epic announcement
:: ============================================================================
echo  [96m[STEP 3/5] Race announcement...[0m
echo.
echo.
echo  [93m  ================================================================[0m
echo  [93m  ^|                                                              ^|[0m
echo  [93m  ^|[0m  [91m        ________  THE RACE HAS BEGUN!  ________[0m          [93m^|[0m
echo  [93m  ^|[0m                                                            [93m^|[0m
echo  [93m  ^|[0m  [97m         Race: !RACE_NAME![0m
echo  [93m  ^|[0m  [97m         ID:   !RACE_ID![0m
echo  [93m  ^|[0m                                                            [93m^|[0m
echo  [93m  ^|[0m  [97m   Check the Race Simulator logs for live commentary![0m    [93m^|[0m
echo  [93m  ^|[0m  [97m   Log: C:\dragon-racing\logs\race-simulator-stdout.log[0m [93m^|[0m
echo  [93m  ^|[0m                                                            [93m^|[0m
echo  [93m  ================================================================[0m
echo.
echo.
echo  [91m           ~*~  /\_/\  ~*~[0m
echo  [91m          ~*~  ( ^> . ^< ) ~*~[0m
echo  [91m           ~*~  ^> ^ ^<  ~*~       [93mGO GO GO!!![0m
echo  [91m          ~*~  /^|   ^|\  ~*~[0m
echo  [91m         ~*~ _/ ^|   ^| \_ ~*~[0m
echo.

:: ============================================================================
:: STEP 4: Wait for the race to complete
:: ============================================================================
echo  [96m[STEP 4/5] Waiting 10 seconds for the race to finish...[0m
echo.

echo   [97m  Live from the Dragon Racing Arena:[0m
echo.

:: Countdown with racing commentary
set "COMMENTS[1]=  [93m    And they're off! Dragons launching from the gates![0m"
set "COMMENTS[2]=  [97m    Shadowclaw takes an early lead with a burst of flame![0m"
set "COMMENTS[3]=  [91m    Stormwing is gaining on the outside -- incredible speed![0m"
set "COMMENTS[4]=  [97m    The pack tightens as they round the Volcano Turn![0m"
set "COMMENTS[5]=  [93m    Frostbite uses an ice blast to slow down the competition![0m"
set "COMMENTS[6]=  [91m    Inferno breathes fire -- literally clearing a path![0m"
set "COMMENTS[7]=  [97m    They're entering the final stretch now![0m"
set "COMMENTS[8]=  [93m    It's neck and neck between Shadowclaw and Stormwing![0m"
set "COMMENTS[9]=  [91m    The crowd goes wild! They're approaching the finish line![0m"
set "COMMENTS[10]= [92m    AND THEY CROSS! What a race!!![0m"

for /l %%i in (1,1,10) do (
    echo !COMMENTS[%%i]!
    timeout /t 1 /nobreak >nul
)
echo.

:: ============================================================================
:: STEP 5: Check for results
:: ============================================================================
echo  [96m[STEP 5/5] Checking race results...[0m
echo.

powershell -Command ^
    "try {" ^
    "  $response = Invoke-WebRequest -Uri 'http://localhost:9081/api/races/%RACE_ID%/results' -UseBasicParsing -TimeoutSec 10;" ^
    "  $results = $response.Content | ConvertFrom-Json;" ^
    "  Write-Host '  [92m[RESULTS AVAILABLE][0m';" ^
    "  Write-Host '';" ^
    "  Write-Host '  [93m  ====== RACE RESULTS ======[0m';" ^
    "  Write-Host '';" ^
    "  if ($results.standings) {" ^
    "    $position = 1;" ^
    "    foreach ($entry in $results.standings) {" ^
    "      $medal = switch ($position) { 1 { '[93m[1st][0m' } 2 { '[97m[2nd][0m' } 3 { '[91m[3rd][0m' } default { '[97m[' + $position + 'th][0m' } };" ^
    "      Write-Host ('    {0} {1} - Time: {2}' -f $medal, $entry.dragonName, $entry.finishTime);" ^
    "      $position++;" ^
    "    }" ^
    "  } else {" ^
    "    Write-Host '  [97m  ' $response.Content;" ^
    "  }" ^
    "  Write-Host '';" ^
    "  Write-Host '  [93m  ===========================[0m';" ^
    "  exit 0;" ^
    "} catch {" ^
    "  Write-Host '  [93m[PENDING][0m Race results not yet available.';" ^
    "  Write-Host ('  [97m          ' + $_.Exception.Message);" ^
    "  Write-Host '  [97m          The race may still be in progress.';" ^
    "  Write-Host '  [97m          Check the simulator log for updates:';" ^
    "  Write-Host '  [97m          C:\dragon-racing\logs\race-simulator-stdout.log';" ^
    "  exit 0;" ^
    "}"

echo.
echo.
echo  [93m============================================================================[0m
echo  [97m  RACE TRIGGER COMPLETE[0m
echo  [93m============================================================================[0m
echo.
echo  [97m  Useful commands:[0m
echo  [97m    - View simulator log:  [92mtype C:\dragon-racing\logs\race-simulator-stdout.log[0m
echo  [97m    - Check leaderboard:   [92mpowershell -Command "Invoke-WebRequest http://localhost:9082/api/leaderboard"[0m
echo  [97m    - Check betting:       [92mpowershell -Command "Invoke-WebRequest http://localhost:5001/api/bets"[0m
echo  [97m    - Run another race:    [92mtrigger-race.bat[0m
echo.
echo  [91m       ~~ May your dragon bring glory and gold! ~~[0m
echo.
echo  [93m============================================================================[0m
echo.

goto :eof

:race_failed
echo.
echo  [91m============================================================================[0m
echo  [91m  RACE TRIGGER FAILED[0m
echo  [91m============================================================================[0m
echo.
echo  [97m  Troubleshooting:[0m
echo  [97m    1. Run [92mcheck-health.bat[97m to verify services are running[0m
echo  [97m    2. Run [92mstart-all.bat[97m if services are not started[0m
echo  [97m    3. Check logs in C:\dragon-racing\logs\[0m
echo.
echo  [91m============================================================================[0m
echo.

endlocal
exit /b 1

:eof
endlocal
exit /b 0
