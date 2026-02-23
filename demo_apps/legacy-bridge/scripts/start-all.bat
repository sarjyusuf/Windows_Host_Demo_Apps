@echo off
REM ============================================================================
REM LegacyBridge - Start All Services
REM ============================================================================
REM Starts all 7 JVM processes in the correct order:
REM   1. ActiveMQ (message broker)
REM   2. Tomcat (WAR deployments: docmgr, auth, api)
REM   3. Tika Processor (standalone JAR, background)
REM   4. Lucene Search (standalone JAR, background)
REM   5. Document Processor (Windows service via WinSW)
REM   6. Batch Runner (Windows service via WinSW)
REM   7. Swing Client (standalone JAR, foreground GUI)
REM ============================================================================

setlocal enabledelayedexpansion

set "SCRIPTS_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPTS_DIR%.."

echo.
echo ======================================================================
echo   LegacyBridge - Starting All Services
echo ======================================================================
echo.

REM ----------------------------------------------------------------------------
REM 1. Start ActiveMQ
REM ----------------------------------------------------------------------------
echo [1/7] Starting ActiveMQ...
set "DD_SERVICE=legacybridge-activemq"
net start ActiveMQ >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] ActiveMQ service started.
) else (
    echo       [!] ActiveMQ service may already be running or is not installed.
    echo       [!] Attempting direct start...
    for /d %%D in ("%PROJECT_ROOT%\infrastructure\activemq\apache-activemq-*") do (
        set "ACTIVEMQ_HOME=%%D"
    )
    if defined ACTIVEMQ_HOME (
        start "ActiveMQ" cmd /c "!ACTIVEMQ_HOME!\bin\activemq.bat" start
        echo       [OK] ActiveMQ started directly.
    ) else (
        echo       [X] ActiveMQ not found. Run setup.ps1 first.
    )
)

echo       Waiting 10 seconds for ActiveMQ to initialize...
timeout /t 10 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 2. Start Tomcat
REM ----------------------------------------------------------------------------
echo [2/7] Starting Tomcat (docmgr.war, auth.war, api.war)...
set "DD_SERVICE=legacybridge-tomcat"
net start LegacyBridge-Tomcat >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Tomcat service started.
) else (
    echo       [!] Tomcat service may already be running or is not installed as a service.
    echo       [!] Attempting direct start...
    for /d %%D in ("%PROJECT_ROOT%\infrastructure\tomcat\apache-tomcat-*") do (
        set "CATALINA_HOME=%%D"
    )
    if defined CATALINA_HOME (
        start "Tomcat" cmd /c "!CATALINA_HOME!\bin\catalina.bat" run
        echo       [OK] Tomcat started directly.
    ) else (
        echo       [X] Tomcat not found. Run setup.ps1 first.
    )
)

echo       Waiting 15 seconds for Tomcat to deploy WAR files...
timeout /t 15 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 3. Start Tika Processor (standalone JAR - direct java.exe)
REM ----------------------------------------------------------------------------
echo [3/7] Starting Tika Processor (port 8081)...
set "DD_SERVICE=legacybridge-tika"
set "TIKA_JAR=%PROJECT_ROOT%\tika-processor\target\tika-processor-1.0-SNAPSHOT.jar"
if exist "%TIKA_JAR%" (
    start "Tika Processor" java -jar "%TIKA_JAR%"
    echo       [OK] Tika Processor started (java -jar).
) else (
    echo       [X] Tika JAR not found at %TIKA_JAR%
    echo       [X] Run setup.ps1 to build the project first.
)

REM Brief pause between JAR starts
timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 4. Start Lucene Search (standalone JAR - direct java.exe)
REM ----------------------------------------------------------------------------
echo [4/7] Starting Lucene Search (port 8082)...
set "DD_SERVICE=legacybridge-lucene"
set "LUCENE_JAR=%PROJECT_ROOT%\lucene-search\target\lucene-search-1.0-SNAPSHOT.jar"
if exist "%LUCENE_JAR%" (
    start "Lucene Search" java -jar "%LUCENE_JAR%"
    echo       [OK] Lucene Search started (java -jar).
) else (
    echo       [X] Lucene JAR not found at %LUCENE_JAR%
    echo       [X] Run setup.ps1 to build the project first.
)

REM Brief pause
timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 5. Start Document Processor (Windows service via WinSW)
REM ----------------------------------------------------------------------------
echo [5/7] Starting Document Processor (port 8083)...
set "DD_SERVICE=legacybridge-doc-processor"
net start LegacyBridge-DocProcessor >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Document Processor service started.
) else (
    echo       [!] Document Processor service may already be running or is not installed.
    echo       [!] Attempting direct start...
    set "DOCPROC_JAR=%PROJECT_ROOT%\document-processor\target\document-processor-1.0-SNAPSHOT.jar"
    if exist "!DOCPROC_JAR!" (
        start "Document Processor" java -jar "!DOCPROC_JAR!"
        echo       [OK] Document Processor started directly (java -jar).
    ) else (
        echo       [X] Document Processor JAR not found.
    )
)

REM Brief pause
timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 6. Start Batch Runner (Windows service via WinSW)
REM ----------------------------------------------------------------------------
echo [6/7] Starting Batch Runner...
set "DD_SERVICE=legacybridge-batch-runner"
net start LegacyBridge-BatchRunner >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo       [OK] Batch Runner service started.
) else (
    echo       [!] Batch Runner service may already be running or is not installed.
    echo       [!] Attempting direct start...
    set "BATCH_JAR=%PROJECT_ROOT%\batch-runner\target\batch-runner-1.0-SNAPSHOT.jar"
    if exist "!BATCH_JAR!" (
        start "Batch Runner" java -jar "!BATCH_JAR!"
        echo       [OK] Batch Runner started directly (java -jar).
    ) else (
        echo       [X] Batch Runner JAR not found.
    )
)

REM Brief pause
timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 7. Start Swing Client (standalone JAR - direct java.exe, GUI)
REM ----------------------------------------------------------------------------
echo [7/7] Starting Swing Desktop Client...
set "DD_SERVICE=legacybridge-swing-client"
set "SWING_JAR=%PROJECT_ROOT%\swing-client\target\swing-client-1.0-SNAPSHOT.jar"
if exist "%SWING_JAR%" (
    start "SwingClient" java -jar "%SWING_JAR%"
    echo       [OK] Swing Client started (java -jar).
) else (
    echo       [X] Swing Client JAR not found at %SWING_JAR%
    echo       [X] Run setup.ps1 to build the project first.
)

REM ----------------------------------------------------------------------------
REM Summary
REM ----------------------------------------------------------------------------
echo.
echo ======================================================================
echo   LegacyBridge - All Services Started
echo ======================================================================
echo.
echo   Port Assignments:
echo     8080  - Tomcat (docmgr, auth, api WARs)
echo     8081  - Tika Processor
echo     8082  - Lucene Search
echo     8083  - Document Processor
echo     61616 - ActiveMQ (JMS)
echo     8161  - ActiveMQ Web Console
echo.
echo   Use check-health.bat to verify all endpoints are responding.
echo   Use stop-all.bat to stop all services.
echo.

endlocal
