@echo off
REM ============================================================================
REM LegacyBridge - Start All (No Windows Services)
REM ============================================================================
REM Starts all 7 JVM processes DIRECTLY using java -jar and native startup
REM scripts. No Windows services are used. Each process is launched via
REM "start" which creates a new cmd window.
REM
REM This mode is critical for SSI testing because it creates java.exe
REM processes DIRECTLY, which is the most straightforward injection scenario.
REM The SSI driver (ddinjector) hooks into process creation and instruments
REM each java.exe as it starts. Running without service wrappers removes
REM any indirection between the process creation and the JVM startup.
REM ============================================================================

setlocal enabledelayedexpansion

set "SCRIPTS_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPTS_DIR%.."

echo.
echo ======================================================================
echo   LegacyBridge - Starting All Processes (No Services Mode)
echo ======================================================================
echo.
echo   This mode launches all JVM processes directly via java -jar.
echo   Ideal for testing Single Step Instrumentation (SSI) on direct
echo   java.exe process creation.
echo.

REM Resolve infrastructure paths
for /d %%D in ("%PROJECT_ROOT%\infrastructure\activemq\apache-activemq-*") do set "ACTIVEMQ_HOME=%%D"
for /d %%D in ("%PROJECT_ROOT%\infrastructure\tomcat\apache-tomcat-*") do set "CATALINA_HOME=%%D"

REM Validate infrastructure
if not defined ACTIVEMQ_HOME (
    echo [X] ActiveMQ not found. Run setup.ps1 first.
    exit /b 1
)
if not defined CATALINA_HOME (
    echo [X] Tomcat not found. Run setup.ps1 first.
    exit /b 1
)

echo   ActiveMQ Home: %ACTIVEMQ_HOME%
echo   Tomcat Home:   %CATALINA_HOME%
echo.

REM ----------------------------------------------------------------------------
REM 1. Start ActiveMQ directly
REM ----------------------------------------------------------------------------
echo [1/7] Starting ActiveMQ (port 61616, console 8161)...
start "ActiveMQ" cmd /c "%ACTIVEMQ_HOME%\bin\activemq.bat" start
echo       [OK] ActiveMQ process launched.

echo       Waiting 10 seconds for ActiveMQ broker to initialize...
timeout /t 10 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 2. Start Tomcat directly via catalina.bat run
REM ----------------------------------------------------------------------------
echo [2/7] Starting Tomcat (port 8080 - docmgr.war, auth.war, api.war)...
start "Tomcat" cmd /c "%CATALINA_HOME%\bin\catalina.bat" run
echo       [OK] Tomcat process launched.

echo       Waiting 15 seconds for Tomcat to deploy WAR files...
timeout /t 15 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 3. Start Tika Processor directly
REM ----------------------------------------------------------------------------
echo [3/7] Starting Tika Processor (port 8081)...
set "TIKA_JAR=%PROJECT_ROOT%\tika-processor\target\tika-processor-1.0-SNAPSHOT.jar"
if exist "%TIKA_JAR%" (
    start "Tika Processor" java -jar "%TIKA_JAR%"
    echo       [OK] Tika Processor launched (java -jar).
) else (
    echo       [X] Tika JAR not found: %TIKA_JAR%
    echo       [X] Run setup.ps1 to build first.
)

timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 4. Start Lucene Search directly
REM ----------------------------------------------------------------------------
echo [4/7] Starting Lucene Search (port 8082)...
set "LUCENE_JAR=%PROJECT_ROOT%\lucene-search\target\lucene-search-1.0-SNAPSHOT.jar"
if exist "%LUCENE_JAR%" (
    start "Lucene Search" java -jar "%LUCENE_JAR%"
    echo       [OK] Lucene Search launched (java -jar).
) else (
    echo       [X] Lucene JAR not found: %LUCENE_JAR%
    echo       [X] Run setup.ps1 to build first.
)

timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 5. Start Document Processor directly
REM ----------------------------------------------------------------------------
echo [5/7] Starting Document Processor (port 8083)...
set "DOCPROC_JAR=%PROJECT_ROOT%\document-processor\target\document-processor-1.0-SNAPSHOT.jar"
if exist "%DOCPROC_JAR%" (
    start "Document Processor" java -jar "%DOCPROC_JAR%"
    echo       [OK] Document Processor launched (java -jar).
) else (
    echo       [X] Document Processor JAR not found: %DOCPROC_JAR%
    echo       [X] Run setup.ps1 to build first.
)

timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 6. Start Batch Runner directly
REM ----------------------------------------------------------------------------
echo [6/7] Starting Batch Runner...
set "BATCH_JAR=%PROJECT_ROOT%\batch-runner\target\batch-runner-1.0-SNAPSHOT.jar"
if exist "%BATCH_JAR%" (
    start "Batch Runner" java -jar "%BATCH_JAR%"
    echo       [OK] Batch Runner launched (java -jar).
) else (
    echo       [X] Batch Runner JAR not found: %BATCH_JAR%
    echo       [X] Run setup.ps1 to build first.
)

timeout /t 3 /nobreak >nul

REM ----------------------------------------------------------------------------
REM 7. Start Swing Client directly
REM ----------------------------------------------------------------------------
echo [7/7] Starting Swing Desktop Client...
set "SWING_JAR=%PROJECT_ROOT%\swing-client\target\swing-client-1.0-SNAPSHOT.jar"
if exist "%SWING_JAR%" (
    start "SwingClient" java -jar "%SWING_JAR%"
    echo       [OK] Swing Client launched (java -jar).
) else (
    echo       [X] Swing Client JAR not found: %SWING_JAR%
    echo       [X] Run setup.ps1 to build first.
)

REM ----------------------------------------------------------------------------
REM Summary
REM ----------------------------------------------------------------------------
echo.
echo ======================================================================
echo   LegacyBridge - All Processes Launched (No Services Mode)
echo ======================================================================
echo.
echo   All 7 JVM processes are running as direct java.exe processes.
echo   Each runs in its own console window.
echo.
echo   Port Assignments:
echo     8080  - Tomcat (docmgr, auth, api WARs)
echo     8081  - Tika Processor
echo     8082  - Lucene Search
echo     8083  - Document Processor
echo     61616 - ActiveMQ (JMS)
echo     8161  - ActiveMQ Web Console
echo.
echo   SSI Testing Notes:
echo     - Each java.exe process was created directly
echo     - Check JAVA_TOOL_OPTIONS in each process environment to verify injection
echo     - Use Process Explorer to inspect injected agent paths
echo     - All processes should show the dd-java-agent if SSI is active
echo.
echo   Use stop-all-no-services.bat to kill all processes.
echo   Use check-health.bat to verify all endpoints.
echo.

endlocal
