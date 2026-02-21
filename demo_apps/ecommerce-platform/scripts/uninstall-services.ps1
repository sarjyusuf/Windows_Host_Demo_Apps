#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Uninstalls all ecommerce-platform Windows Services and cleans up.
.DESCRIPTION
    Stops and removes the OrderProcessor and NotificationWorker Windows Services.
    Optionally removes published files and data directories.
#>

param(
    [string]$PublishRoot = "C:\DemoApps\ecommerce",
    [switch]$RemoveData,
    [switch]$RemoveAll
)

$ErrorActionPreference = "Continue"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " E-Commerce Platform - Uninstall" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# --- Stop and Remove Windows Services ---
Write-Host "Removing Windows Services..." -ForegroundColor Yellow

$windowsServices = @("EcommerceOrderProcessor", "EcommerceNotificationWorker")

foreach ($svcName in $windowsServices) {
    $svc = Get-Service -Name $svcName -ErrorAction SilentlyContinue
    if (-not $svc) {
        Write-Host "  $svcName not installed, skipping" -ForegroundColor DarkGray
        continue
    }

    # Stop if running
    if ($svc.Status -ne "Stopped") {
        Write-Host "  Stopping $svcName..." -ForegroundColor White
        Stop-Service -Name $svcName -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 2
    }

    # Delete the service
    Write-Host "  Removing $svcName..." -ForegroundColor White -NoNewline
    sc.exe delete $svcName | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host " Removed" -ForegroundColor Green
    } else {
        Write-Host " Failed (may need reboot)" -ForegroundColor Yellow
    }
}

# --- Stop any running Web API processes ---
Write-Host ""
Write-Host "Stopping Web API processes..." -ForegroundColor Yellow

$processNames = @("WebStorefront", "OrderApi", "InventoryApi")
foreach ($procName in $processNames) {
    $processes = Get-Process -Name $procName -ErrorAction SilentlyContinue
    if ($processes) {
        foreach ($proc in $processes) {
            $proc.Kill()
            Write-Host "  Killed $procName (PID: $($proc.Id))" -ForegroundColor Gray
        }
    }
}

# --- Optional cleanup ---
if ($RemoveData -or $RemoveAll) {
    Write-Host ""
    Write-Host "Removing data directories..." -ForegroundColor Yellow

    $dataDirs = @(
        "$PublishRoot\data",
        "$PublishRoot\queues",
        "$PublishRoot\logs"
    )

    foreach ($dir in $dataDirs) {
        if (Test-Path $dir) {
            Remove-Item -Path $dir -Recurse -Force
            Write-Host "  Removed: $dir" -ForegroundColor Gray
        }
    }
}

if ($RemoveAll) {
    Write-Host ""
    Write-Host "Removing all published files..." -ForegroundColor Yellow

    if (Test-Path $PublishRoot) {
        Remove-Item -Path $PublishRoot -Recurse -Force
        Write-Host "  Removed: $PublishRoot" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "Uninstall complete." -ForegroundColor Cyan
Write-Host ""
if (-not $RemoveAll) {
    Write-Host "Note: Published binaries remain at $PublishRoot\services\" -ForegroundColor Gray
    Write-Host "Use -RemoveData to also remove data/queues/logs" -ForegroundColor Gray
    Write-Host "Use -RemoveAll to remove everything under $PublishRoot" -ForegroundColor Gray
}
Write-Host ""
