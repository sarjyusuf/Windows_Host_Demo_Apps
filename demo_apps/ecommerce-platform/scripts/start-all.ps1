#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Starts all ecommerce-platform services.
.DESCRIPTION
    Starts the three ASP.NET Core web APIs as background processes and
    the two Worker Services via Windows Service Control Manager.
#>

param(
    [string]$PublishRoot = "C:\DemoApps\ecommerce"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " E-Commerce Platform - Start All" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# --- Start ASP.NET Core Web APIs as background processes ---
$webApis = @(
    @{
        Name    = "WebStorefront"
        ExePath = "$PublishRoot\services\WebStorefront\WebStorefront.exe"
        Port    = 5100
    },
    @{
        Name    = "OrderApi"
        ExePath = "$PublishRoot\services\OrderApi\OrderApi.exe"
        Port    = 5101
    },
    @{
        Name    = "InventoryApi"
        ExePath = "$PublishRoot\services\InventoryApi\InventoryApi.exe"
        Port    = 5102
    }
)

Write-Host "Starting Web APIs..." -ForegroundColor Yellow
foreach ($api in $webApis) {
    if (-not (Test-Path $api.ExePath)) {
        Write-Host "  ERROR: $($api.ExePath) not found. Run setup.ps1 first." -ForegroundColor Red
        continue
    }

    # Check if already running
    $processName = [System.IO.Path]::GetFileNameWithoutExtension($api.ExePath)
    $existing = Get-Process -Name $processName -ErrorAction SilentlyContinue
    if ($existing) {
        Write-Host "  $($api.Name) already running (PID: $($existing.Id))" -ForegroundColor DarkYellow
        continue
    }

    $logFile = "$PublishRoot\logs\$($api.Name).log"
    $process = Start-Process -FilePath $api.ExePath `
        -WorkingDirectory (Split-Path $api.ExePath) `
        -WindowStyle Hidden `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError "$PublishRoot\logs\$($api.Name)-error.log" `
        -PassThru

    Write-Host "  $($api.Name) started (PID: $($process.Id), Port: $($api.Port))" -ForegroundColor Green
    Write-Host "    Log: $logFile" -ForegroundColor Gray
}

# --- Start Windows Services ---
Write-Host ""
Write-Host "Starting Windows Services..." -ForegroundColor Yellow

$windowsServices = @("EcommerceOrderProcessor", "EcommerceNotificationWorker")

foreach ($svcName in $windowsServices) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Host "  Service '$svcName' not found. Run install-services.ps1 first." -ForegroundColor Red
        continue
    }

    if ($svc.Status -eq "Running") {
        Write-Host "  $svcName already running" -ForegroundColor DarkYellow
        continue
    }

    Start-Service -Name $svcName
    Start-Sleep -Seconds 2
    $svc = Get-Service -Name $svcName
    if ($svc.Status -eq "Running") {
        Write-Host "  $svcName started successfully" -ForegroundColor Green
    } else {
        Write-Host "  $svcName status: $($svc.Status)" -ForegroundColor Red
    }
}

# --- Health Check ---
Write-Host ""
Write-Host "Waiting 5 seconds for services to initialize..." -ForegroundColor Gray
Start-Sleep -Seconds 5

Write-Host ""
Write-Host "Service Status:" -ForegroundColor Cyan
Write-Host "  Web APIs:" -ForegroundColor Yellow
foreach ($api in $webApis) {
    try {
        $response = Invoke-WebRequest -Uri "http://localhost:$($api.Port)/api/health" -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        Write-Host "    $($api.Name) (port $($api.Port)): OK ($($response.StatusCode))" -ForegroundColor Green
    } catch {
        # Try a simple TCP check
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("localhost", $api.Port)
            $tcp.Close()
            Write-Host "    $($api.Name) (port $($api.Port)): Listening (no health endpoint)" -ForegroundColor Yellow
        } catch {
            Write-Host "    $($api.Name) (port $($api.Port)): Not responding" -ForegroundColor Red
        }
    }
}

Write-Host "  Windows Services:" -ForegroundColor Yellow
foreach ($svcName in $windowsServices) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if ($svc) {
        $color = if ($svc.Status -eq "Running") { "Green" } else { "Red" }
        Write-Host "    $svcName : $($svc.Status)" -ForegroundColor $color
    }
}

Write-Host ""
Write-Host "All services started. Endpoints:" -ForegroundColor Cyan
Write-Host "  WebStorefront:  http://localhost:5100" -ForegroundColor White
Write-Host "  OrderApi:       http://localhost:5101" -ForegroundColor White
Write-Host "  InventoryApi:   http://localhost:5102" -ForegroundColor White
Write-Host ""
