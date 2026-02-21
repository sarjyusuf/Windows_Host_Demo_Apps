#Requires -Version 5.0
#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Installs LegacyBridge components as Windows services.

.DESCRIPTION
    This script installs the following Windows services:
    1. LegacyBridge-Tomcat       - via Apache Commons Daemon (prunsrv.exe)
    2. LegacyBridge-DocProcessor - via WinSW
    3. LegacyBridge-BatchRunner  - via WinSW
    4. LegacyBridge-ActiveMQ     - via ActiveMQ built-in service installer

    Must be run as Administrator.

.NOTES
    Run setup.ps1 first to download all dependencies and build the project.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================================
# Configuration
# ============================================================================
$ScriptsDir      = $PSScriptRoot
$ProjectRoot     = (Resolve-Path (Join-Path $ScriptsDir "..")).Path
$InfraDir        = Join-Path $ProjectRoot "infrastructure"
$WinSWDir        = Join-Path $ScriptsDir "winsw"

# Locate Tomcat and ActiveMQ directories
$TomcatHome      = Get-ChildItem -Path (Join-Path $InfraDir "tomcat") -Directory -Filter "apache-tomcat-*" | Select-Object -First 1 -ExpandProperty FullName
$ActiveMQHome    = Get-ChildItem -Path (Join-Path $InfraDir "activemq") -Directory -Filter "apache-activemq-*" | Select-Object -First 1 -ExpandProperty FullName

if (-not $TomcatHome) {
    Write-Host "[X] Tomcat not found. Run setup.ps1 first." -ForegroundColor Red
    exit 1
}
if (-not $ActiveMQHome) {
    Write-Host "[X] ActiveMQ not found. Run setup.ps1 first." -ForegroundColor Red
    exit 1
}

$WinSWExe        = Join-Path $WinSWDir "WinSW-x64.exe"
if (-not (Test-Path $WinSWExe)) {
    Write-Host "[X] WinSW-x64.exe not found at $WinSWExe. Run setup.ps1 first." -ForegroundColor Red
    exit 1
}

# JAVA_HOME fallback
$JavaHome = $env:JAVA_HOME
if (-not $JavaHome) {
    $javaPath = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaPath) {
        # Resolve symlinks and get the JDK root (up two levels from bin/java.exe)
        $JavaHome = (Resolve-Path (Join-Path (Split-Path $javaPath) "..")).Path
    }
    else {
        Write-Host "[X] Java not found. Set JAVA_HOME or ensure java is in PATH." -ForegroundColor Red
        exit 1
    }
}

$JvmDll = Join-Path $JavaHome "bin\server\jvm.dll"
if (-not (Test-Path $JvmDll)) {
    $JvmDll = Join-Path $JavaHome "lib\server\jvm.dll"
}
if (-not (Test-Path $JvmDll)) {
    # Search for jvm.dll recursively
    $JvmDll = Get-ChildItem -Path $JavaHome -Recurse -Filter "jvm.dll" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}

Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host "  Installing LegacyBridge Windows Services" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host ""
Write-Host "  Tomcat Home:   $TomcatHome" -ForegroundColor White
Write-Host "  ActiveMQ Home: $ActiveMQHome" -ForegroundColor White
Write-Host "  JAVA_HOME:     $JavaHome" -ForegroundColor White
Write-Host "  JVM DLL:       $JvmDll" -ForegroundColor White
Write-Host ""

# ============================================================================
# 1. Install Tomcat via prunsrv.exe (Apache Commons Daemon)
# ============================================================================
Write-Host "[1/4] Installing Tomcat service via prunsrv.exe..." -ForegroundColor Green

$prunsrvExe = Join-Path $TomcatHome "bin\prunsrv.exe"

if (-not (Test-Path $prunsrvExe)) {
    Write-Host "  [!] prunsrv.exe not found at $prunsrvExe" -ForegroundColor Yellow
    Write-Host "  [!] Checking for Tomcat service.bat installer instead..." -ForegroundColor Yellow

    $serviceBat = Join-Path $TomcatHome "bin\service.bat"
    if (Test-Path $serviceBat) {
        Write-Host "  Using service.bat to install Tomcat service..." -ForegroundColor White
        $env:CATALINA_HOME = $TomcatHome
        & cmd /c "`"$serviceBat`" install LegacyBridge-Tomcat" 2>&1 | ForEach-Object { Write-Host "    $_" }
    }
    else {
        Write-Host "  [X] Neither prunsrv.exe nor service.bat found. Tomcat service cannot be installed." -ForegroundColor Red
        Write-Host "  [X] You may need to download the full Tomcat distribution (not the 'core' zip)." -ForegroundColor Red
    }
}
else {
    $tomcatClasspath = Join-Path $TomcatHome "bin\bootstrap.jar"
    $tomcatClasspath += ";" + (Join-Path $TomcatHome "bin\tomcat-juli.jar")

    $prunsrvArgs = @(
        "//IS//LegacyBridge-Tomcat",
        "--DisplayName=LegacyBridge Tomcat",
        "--Description=LegacyBridge Application Server (Apache Tomcat)",
        "--Startup=manual",
        "--StartMode=jvm",
        "--StopMode=jvm",
        "--StartClass=org.apache.catalina.startup.Bootstrap",
        "--StartParams=start",
        "--StopClass=org.apache.catalina.startup.Bootstrap",
        "--StopParams=stop",
        "--Classpath=$tomcatClasspath",
        "--LogPath=$(Join-Path $TomcatHome 'logs')",
        "--LogLevel=Info",
        "--StdOutput=auto",
        "--StdError=auto"
    )

    # Add JVM path if found
    if ($JvmDll -and (Test-Path $JvmDll)) {
        $prunsrvArgs += "--Jvm=$JvmDll"
    }

    # Add JVM options
    $jvmOpts = @(
        "-Dcatalina.home=$TomcatHome",
        "-Dcatalina.base=$TomcatHome",
        "-Djava.io.tmpdir=$(Join-Path $TomcatHome 'temp')",
        "-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager",
        "-Djava.util.logging.config.file=$(Join-Path $TomcatHome 'conf\logging.properties')",
        "-Xms256m",
        "-Xmx512m"
    )
    $jvmOptsString = $jvmOpts -join ";"
    $prunsrvArgs += "--JvmOptions=$jvmOptsString"

    Write-Host "  Executing: $prunsrvExe $($prunsrvArgs -join ' ')" -ForegroundColor Gray
    & $prunsrvExe $prunsrvArgs 2>&1 | ForEach-Object { Write-Host "    $_" }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Tomcat service installed successfully." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] prunsrv returned exit code $LASTEXITCODE. Service may already exist." -ForegroundColor Yellow
    }
}

# ============================================================================
# 2. Install Document Processor via WinSW
# ============================================================================
Write-Host ""
Write-Host "[2/4] Installing Document Processor service via WinSW..." -ForegroundColor Green

$docProcServiceExe = Join-Path $WinSWDir "document-processor-service.exe"
$docProcServiceXml = Join-Path $WinSWDir "document-processor-service.xml"

# Copy WinSW exe with service-specific name
if (-not (Test-Path $docProcServiceExe)) {
    Copy-Item -Path $WinSWExe -Destination $docProcServiceExe -Force
    Write-Host "  Copied WinSW as document-processor-service.exe" -ForegroundColor Gray
}

# Verify XML config exists
if (-not (Test-Path $docProcServiceXml)) {
    Write-Host "  [X] document-processor-service.xml not found at $docProcServiceXml" -ForegroundColor Red
    Write-Host "  [X] Ensure the WinSW XML config files are in the scripts/winsw/ directory." -ForegroundColor Red
}
else {
    & $docProcServiceExe install 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Document Processor service installed successfully." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] WinSW install returned exit code $LASTEXITCODE. Service may already exist." -ForegroundColor Yellow
    }
}

# ============================================================================
# 3. Install Batch Runner via WinSW
# ============================================================================
Write-Host ""
Write-Host "[3/4] Installing Batch Runner service via WinSW..." -ForegroundColor Green

$batchRunnerServiceExe = Join-Path $WinSWDir "batch-runner-service.exe"
$batchRunnerServiceXml = Join-Path $WinSWDir "batch-runner-service.xml"

# Copy WinSW exe with service-specific name
if (-not (Test-Path $batchRunnerServiceExe)) {
    Copy-Item -Path $WinSWExe -Destination $batchRunnerServiceExe -Force
    Write-Host "  Copied WinSW as batch-runner-service.exe" -ForegroundColor Gray
}

# Verify XML config exists
if (-not (Test-Path $batchRunnerServiceXml)) {
    Write-Host "  [X] batch-runner-service.xml not found at $batchRunnerServiceXml" -ForegroundColor Red
    Write-Host "  [X] Ensure the WinSW XML config files are in the scripts/winsw/ directory." -ForegroundColor Red
}
else {
    & $batchRunnerServiceExe install 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Batch Runner service installed successfully." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] WinSW install returned exit code $LASTEXITCODE. Service may already exist." -ForegroundColor Yellow
    }
}

# ============================================================================
# 4. Install ActiveMQ via built-in service installer
# ============================================================================
Write-Host ""
Write-Host "[4/4] Installing ActiveMQ service..." -ForegroundColor Green

$activemqBat = Join-Path $ActiveMQHome "bin\activemq.bat"

if (Test-Path $activemqBat) {
    & cmd /c "`"$activemqBat`" install" 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] ActiveMQ service installed successfully." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] ActiveMQ install returned exit code $LASTEXITCODE. Service may already exist." -ForegroundColor Yellow
    }
}
else {
    Write-Host "  [X] activemq.bat not found at $activemqBat" -ForegroundColor Red
}

# ============================================================================
# Summary - Print Service Status
# ============================================================================
Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host "  Service Installation Complete" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host ""

$serviceNames = @(
    "LegacyBridge-Tomcat",
    "LegacyBridge-DocProcessor",
    "LegacyBridge-BatchRunner",
    "ActiveMQ"
)

Write-Host "  Service Status:" -ForegroundColor White
Write-Host "  -----------------------------------------------" -ForegroundColor Gray
foreach ($svcName in $serviceNames) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if ($svc) {
        $status = $svc.Status
        $color = if ($status -eq "Running") { "Green" } else { "Yellow" }
        Write-Host ("  {0,-35} {1}" -f $svcName, $status) -ForegroundColor $color
    }
    else {
        Write-Host ("  {0,-35} {1}" -f $svcName, "NOT FOUND") -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "  Use start-all.bat to start all services." -ForegroundColor Yellow
Write-Host "  Use uninstall-services.ps1 to remove all services." -ForegroundColor Yellow
Write-Host ""
