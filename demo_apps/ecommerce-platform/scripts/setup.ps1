#Requires -RunAsAdministrator
<#
.SYNOPSIS
    Builds and publishes all ecommerce-platform services for Windows deployment.
.DESCRIPTION
    Publishes all .NET 8 services to C:\DemoApps\ecommerce\services\ and creates
    required data and queue directories.
#>

param(
    [string]$PublishRoot = "C:\DemoApps\ecommerce",
    [string]$Configuration = "Release"
)

$ErrorActionPreference = "Stop"
$SrcRoot = Split-Path -Parent $PSScriptRoot | Join-Path -ChildPath "src"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host " E-Commerce Platform - Setup" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Verify .NET 8 SDK is installed
Write-Host "[1/4] Checking .NET SDK..." -ForegroundColor Yellow
$dotnetVersion = dotnet --version
if (-not $dotnetVersion.StartsWith("8.")) {
    Write-Warning ".NET 8 SDK not detected (found: $dotnetVersion). Ensure .NET 8+ SDK is installed."
}
Write-Host "  .NET SDK version: $dotnetVersion" -ForegroundColor Green

# Create directory structure
Write-Host ""
Write-Host "[2/4] Creating directory structure..." -ForegroundColor Yellow

$directories = @(
    "$PublishRoot\services\WebStorefront",
    "$PublishRoot\services\OrderApi",
    "$PublishRoot\services\InventoryApi",
    "$PublishRoot\services\OrderProcessor",
    "$PublishRoot\services\NotificationWorker",
    "$PublishRoot\data",
    "$PublishRoot\queues\order-events\pending",
    "$PublishRoot\queues\order-events\processing",
    "$PublishRoot\queues\order-events\completed",
    "$PublishRoot\queues\order-events\failed",
    "$PublishRoot\queues\fulfillment-events\pending",
    "$PublishRoot\queues\fulfillment-events\processing",
    "$PublishRoot\queues\fulfillment-events\completed",
    "$PublishRoot\queues\fulfillment-events\failed",
    "$PublishRoot\logs"
)

foreach ($dir in $directories) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Host "  Created: $dir" -ForegroundColor Gray
    } else {
        Write-Host "  Exists:  $dir" -ForegroundColor DarkGray
    }
}

# Publish all services
Write-Host ""
Write-Host "[3/4] Publishing services..." -ForegroundColor Yellow

$services = @(
    @{ Name = "WebStorefront";      Project = "WebStorefront\WebStorefront.csproj" },
    @{ Name = "OrderApi";           Project = "OrderApi\OrderApi.csproj" },
    @{ Name = "InventoryApi";       Project = "InventoryApi\InventoryApi.csproj" },
    @{ Name = "OrderProcessor";     Project = "OrderProcessor\OrderProcessor.csproj" },
    @{ Name = "NotificationWorker"; Project = "NotificationWorker\NotificationWorker.csproj" }
)

foreach ($svc in $services) {
    $projectPath = Join-Path $SrcRoot $svc.Project
    $outputPath = Join-Path "$PublishRoot\services" $svc.Name

    Write-Host "  Publishing $($svc.Name)..." -ForegroundColor White -NoNewline

    dotnet publish $projectPath `
        -c $Configuration `
        -o $outputPath `
        -r win-x64 `
        --self-contained false `
        --nologo `
        -v quiet

    if ($LASTEXITCODE -ne 0) {
        Write-Host " FAILED" -ForegroundColor Red
        throw "Failed to publish $($svc.Name)"
    }
    Write-Host " OK" -ForegroundColor Green
}

# Summary
Write-Host ""
Write-Host "[4/4] Setup complete!" -ForegroundColor Yellow
Write-Host ""
Write-Host "Published services:" -ForegroundColor Cyan
foreach ($svc in $services) {
    $exePath = Join-Path "$PublishRoot\services\$($svc.Name)" "$($svc.Name).exe"
    $exists = Test-Path $exePath
    $status = if ($exists) { "Ready" } else { "Missing EXE" }
    $color = if ($exists) { "Green" } else { "Red" }
    Write-Host "  $($svc.Name.PadRight(25)) $status" -ForegroundColor $color
}

Write-Host ""
Write-Host "Data directory:   $PublishRoot\data" -ForegroundColor Gray
Write-Host "Queue directory:  $PublishRoot\queues" -ForegroundColor Gray
Write-Host "Log directory:    $PublishRoot\logs" -ForegroundColor Gray
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Run .\install-services.ps1 to register Windows Services"
Write-Host "  2. Run .\start-all.ps1 to start all services"
Write-Host ""
