#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Registers OrderProcessor and NotificationWorker as Windows Services.
.DESCRIPTION
    Uses sc.exe to create Windows Services for the background worker processes.
    These services will be managed by the Windows Service Control Manager (SCM).
#>

param(
    [string]$PublishRoot = "C:\DemoApps\ecommerce"
)

$ErrorActionPreference = "Stop"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " E-Commerce Platform - Install Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$windowsServices = @(
    @{
        Name        = "EcommerceOrderProcessor"
        DisplayName = "Ecommerce Order Processor"
        Description = "Processes incoming orders from the file-based message queue, reserves inventory, and publishes fulfillment events."
        ExePath     = "$PublishRoot\services\OrderProcessor\OrderProcessor.exe"
    },
    @{
        Name        = "EcommerceNotificationWorker"
        DisplayName = "Ecommerce Notification Worker"
        Description = "Watches for fulfilled orders and sends simulated email/SMS notifications."
        ExePath     = "$PublishRoot\services\NotificationWorker\NotificationWorker.exe"
    }
)

foreach ($svc in $windowsServices) {
    Write-Host "Installing $($svc.DisplayName)..." -ForegroundColor Yellow

    # Check if service already exists
    $existingService = Get-Service -Name $svc.Name -ErrorAction SilentlyContinue
    if ($existingService) {
        Write-Host "  Service '$($svc.Name)' already exists. Stopping and removing..." -ForegroundColor DarkYellow
        if ($existingService.Status -eq "Running") {
            Stop-Service -Name $svc.Name -Force
            Start-Sleep -Seconds 2
        }
        sc.exe delete $svc.Name | Out-Null
        Start-Sleep -Seconds 1
    }

    # Verify the executable exists
    if (-not (Test-Path $svc.ExePath)) {
        Write-Host "  ERROR: Executable not found at $($svc.ExePath)" -ForegroundColor Red
        Write-Host "  Run setup.ps1 first to publish the services." -ForegroundColor Red
        continue
    }

    # Create the Windows Service
    $binPath = "`"$($svc.ExePath)`""
    sc.exe create $svc.Name `
        binPath= $binPath `
        start= delayed-auto `
        DisplayName= "`"$($svc.DisplayName)`""

    if ($LASTEXITCODE -ne 0) {
        Write-Host "  FAILED to create service $($svc.Name)" -ForegroundColor Red
        continue
    }

    # Set the description
    sc.exe description $svc.Name "`"$($svc.Description)`""

    # Configure recovery options: restart on first, second, and subsequent failures
    sc.exe failure $svc.Name reset= 86400 actions= restart/5000/restart/10000/restart/30000

    # Set service to run as LocalSystem (default) - suitable for demo
    Write-Host "  Service '$($svc.Name)' installed successfully" -ForegroundColor Green
    Write-Host "    Display Name: $($svc.DisplayName)" -ForegroundColor Gray
    Write-Host "    Startup Type: Automatic (Delayed)" -ForegroundColor Gray
    Write-Host "    Binary Path:  $($svc.ExePath)" -ForegroundColor Gray
    Write-Host ""
}

Write-Host "Windows Services installed." -ForegroundColor Cyan
Write-Host ""
Write-Host "To start services, run: .\start-all.ps1" -ForegroundColor Gray
Write-Host "To check status:  Get-Service Ecommerce*" -ForegroundColor Gray
Write-Host ""
