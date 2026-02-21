#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Stops all ecommerce-platform services.
.DESCRIPTION
    Stops the three ASP.NET Core web API processes and the two Windows Services.
#>

param(
    [string]$PublishRoot = "C:\DemoApps\ecommerce"
)

$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " E-Commerce Platform - Stop All" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# --- Stop Windows Services ---
Write-Host "Stopping Windows Services..." -ForegroundColor Yellow

$windowsServices = @("EcommerceOrderProcessor", "EcommerceNotificationWorker")

foreach ($svcName in $windowsServices) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Host "  $svcName not installed, skipping" -ForegroundColor DarkGray
        continue
    }

    if ($svc.Status -eq "Stopped") {
        Write-Host "  $svcName already stopped" -ForegroundColor DarkGray
        continue
    }

    Write-Host "  Stopping $svcName..." -ForegroundColor White -NoNewline
    Stop-Service -Name $svcName -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2

    $svc = Get-Service -Name $svcName
    if ($svc.Status -eq "Stopped") {
        Write-Host " Stopped" -ForegroundColor Green
    } else {
        Write-Host " $($svc.Status)" -ForegroundColor Yellow
    }
}

# --- Stop Web API Processes ---
Write-Host ""
Write-Host "Stopping Web API processes..." -ForegroundColor Yellow

$processNames = @("WebStorefront", "OrderApi", "InventoryApi")

foreach ($procName in $processNames) {
    $processes = Get-Process -Name $procName -ErrorAction SilentlyContinue
    if (-not $processes) {
        Write-Host "  $procName not running" -ForegroundColor DarkGray
        continue
    }

    foreach ($proc in $processes) {
        Write-Host "  Stopping $procName (PID: $($proc.Id))..." -ForegroundColor White -NoNewline
        try {
            $proc.Kill()
            $proc.WaitForExit(5000) | Out-Null
            Write-Host " Stopped" -ForegroundColor Green
        } catch {
            Write-Host " Failed: $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "All services stopped." -ForegroundColor Cyan
Write-Host ""
