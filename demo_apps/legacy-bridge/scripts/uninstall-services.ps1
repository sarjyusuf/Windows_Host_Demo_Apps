#Requires -Version 5.0
#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Uninstalls all LegacyBridge Windows services.

.DESCRIPTION
    Stops and removes the following Windows services:
    1. LegacyBridge-Tomcat       - via prunsrv.exe //DS//
    2. LegacyBridge-DocProcessor - via WinSW uninstall
    3. LegacyBridge-BatchRunner  - via WinSW uninstall
    4. ActiveMQ                  - via activemq remove

    Must be run as Administrator.
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
$TomcatHome      = Get-ChildItem -Path (Join-Path $InfraDir "tomcat") -Directory -Filter "apache-tomcat-*" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$ActiveMQHome    = Get-ChildItem -Path (Join-Path $InfraDir "activemq") -Directory -Filter "apache-activemq-*" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName

Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host "  Uninstalling LegacyBridge Windows Services" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# Helper: Stop a service if it is running
# ============================================================================
function Stop-ServiceSafe {
    param([string]$ServiceName)

    $svc = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
    if ($svc -and $svc.Status -eq "Running") {
        Write-Host "  Stopping $ServiceName..." -ForegroundColor Yellow
        Stop-Service -Name $ServiceName -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
        Write-Host "  $ServiceName stopped." -ForegroundColor Gray
    }
}

# ============================================================================
# 1. Uninstall Tomcat service
# ============================================================================
Write-Host "[1/4] Uninstalling Tomcat service..." -ForegroundColor Green

Stop-ServiceSafe -ServiceName "LegacyBridge-Tomcat"

$prunsrvExe = if ($TomcatHome) { Join-Path $TomcatHome "bin\prunsrv.exe" } else { $null }

if ($prunsrvExe -and (Test-Path $prunsrvExe)) {
    & $prunsrvExe "//DS//LegacyBridge-Tomcat" 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Tomcat service removed." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] prunsrv returned exit code $LASTEXITCODE." -ForegroundColor Yellow
    }
}
else {
    # Try using service.bat if prunsrv is not available
    $serviceBat = if ($TomcatHome) { Join-Path $TomcatHome "bin\service.bat" } else { $null }
    if ($serviceBat -and (Test-Path $serviceBat)) {
        $env:CATALINA_HOME = $TomcatHome
        & cmd /c "`"$serviceBat`" remove LegacyBridge-Tomcat" 2>&1 | ForEach-Object { Write-Host "    $_" }
    }
    else {
        # Fallback: use sc.exe to delete the service
        $svc = Get-Service -Name "LegacyBridge-Tomcat" -ErrorAction SilentlyContinue
        if ($svc) {
            & sc.exe delete "LegacyBridge-Tomcat" 2>&1 | ForEach-Object { Write-Host "    $_" }
            Write-Host "  [OK] Tomcat service removed via sc.exe." -ForegroundColor Green
        }
        else {
            Write-Host "  [OK] Tomcat service was not installed. Nothing to remove." -ForegroundColor Gray
        }
    }
}

# ============================================================================
# 2. Uninstall Document Processor service
# ============================================================================
Write-Host ""
Write-Host "[2/4] Uninstalling Document Processor service..." -ForegroundColor Green

Stop-ServiceSafe -ServiceName "LegacyBridge-DocProcessor"

$docProcServiceExe = Join-Path $WinSWDir "document-processor-service.exe"

if (Test-Path $docProcServiceExe) {
    & $docProcServiceExe uninstall 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Document Processor service removed." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] WinSW uninstall returned exit code $LASTEXITCODE." -ForegroundColor Yellow
    }
}
else {
    $svc = Get-Service -Name "LegacyBridge-DocProcessor" -ErrorAction SilentlyContinue
    if ($svc) {
        & sc.exe delete "LegacyBridge-DocProcessor" 2>&1 | ForEach-Object { Write-Host "    $_" }
        Write-Host "  [OK] Document Processor service removed via sc.exe." -ForegroundColor Green
    }
    else {
        Write-Host "  [OK] Document Processor service was not installed. Nothing to remove." -ForegroundColor Gray
    }
}

# ============================================================================
# 3. Uninstall Batch Runner service
# ============================================================================
Write-Host ""
Write-Host "[3/4] Uninstalling Batch Runner service..." -ForegroundColor Green

Stop-ServiceSafe -ServiceName "LegacyBridge-BatchRunner"

$batchRunnerServiceExe = Join-Path $WinSWDir "batch-runner-service.exe"

if (Test-Path $batchRunnerServiceExe) {
    & $batchRunnerServiceExe uninstall 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Batch Runner service removed." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] WinSW uninstall returned exit code $LASTEXITCODE." -ForegroundColor Yellow
    }
}
else {
    $svc = Get-Service -Name "LegacyBridge-BatchRunner" -ErrorAction SilentlyContinue
    if ($svc) {
        & sc.exe delete "LegacyBridge-BatchRunner" 2>&1 | ForEach-Object { Write-Host "    $_" }
        Write-Host "  [OK] Batch Runner service removed via sc.exe." -ForegroundColor Green
    }
    else {
        Write-Host "  [OK] Batch Runner service was not installed. Nothing to remove." -ForegroundColor Gray
    }
}

# ============================================================================
# 4. Uninstall ActiveMQ service
# ============================================================================
Write-Host ""
Write-Host "[4/4] Uninstalling ActiveMQ service..." -ForegroundColor Green

Stop-ServiceSafe -ServiceName "ActiveMQ"

$activemqBat = if ($ActiveMQHome) { Join-Path $ActiveMQHome "bin\activemq.bat" } else { $null }

if ($activemqBat -and (Test-Path $activemqBat)) {
    & cmd /c "`"$activemqBat`" remove" 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] ActiveMQ service removed." -ForegroundColor Green
    }
    else {
        Write-Host "  [!] ActiveMQ remove returned exit code $LASTEXITCODE." -ForegroundColor Yellow
    }
}
else {
    $svc = Get-Service -Name "ActiveMQ" -ErrorAction SilentlyContinue
    if ($svc) {
        & sc.exe delete "ActiveMQ" 2>&1 | ForEach-Object { Write-Host "    $_" }
        Write-Host "  [OK] ActiveMQ service removed via sc.exe." -ForegroundColor Green
    }
    else {
        Write-Host "  [OK] ActiveMQ service was not installed. Nothing to remove." -ForegroundColor Gray
    }
}

# ============================================================================
# Summary
# ============================================================================
Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host "  Service Uninstallation Complete" -ForegroundColor Cyan
Write-Host ("=" * 70) -ForegroundColor Cyan
Write-Host ""

$serviceNames = @("LegacyBridge-Tomcat", "LegacyBridge-DocProcessor", "LegacyBridge-BatchRunner", "ActiveMQ")

Write-Host "  Verification:" -ForegroundColor White
foreach ($svcName in $serviceNames) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if ($svc) {
        Write-Host "  [!] $svcName still exists (status: $($svc.Status)). May require reboot to fully remove." -ForegroundColor Yellow
    }
    else {
        Write-Host "  [OK] $svcName removed successfully." -ForegroundColor Green
    }
}
Write-Host ""
