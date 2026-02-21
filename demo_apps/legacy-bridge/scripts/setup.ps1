#Requires -Version 5.0
<#
.SYNOPSIS
    LegacyBridge Setup Script - Downloads infrastructure, builds project, and prepares deployment.

.DESCRIPTION
    This script performs end-to-end setup of the LegacyBridge Enterprise Document Management System:
    1. Validates prerequisites (Java 11+, Maven 3.6+)
    2. Downloads Apache Tomcat 9.0.84
    3. Downloads Apache ActiveMQ 5.18.3
    4. Downloads WinSW v2.12.0
    5. Builds the Maven project
    6. Deploys WAR files to Tomcat
    7. Copies custom configuration files
    8. Creates data directories

.NOTES
    Run this script from any location - it uses $PSScriptRoot for all relative paths.
    Requires internet access for downloads.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ============================================================================
# Configuration
# ============================================================================
$TOMCAT_VERSION    = "9.0.84"
$ACTIVEMQ_VERSION  = "5.18.3"
$WINSW_VERSION     = "2.12.0"

$TOMCAT_ZIP_NAME   = "apache-tomcat-$TOMCAT_VERSION-windows-x64.zip"
$TOMCAT_DIR_NAME   = "apache-tomcat-$TOMCAT_VERSION"
$ACTIVEMQ_ZIP_NAME = "apache-activemq-$ACTIVEMQ_VERSION-bin.zip"
$ACTIVEMQ_DIR_NAME = "apache-activemq-$ACTIVEMQ_VERSION"

$TOMCAT_DOWNLOAD_URL   = "https://archive.apache.org/dist/tomcat/tomcat-9/v$TOMCAT_VERSION/bin/$TOMCAT_ZIP_NAME"
$ACTIVEMQ_DOWNLOAD_URL = "https://archive.apache.org/dist/activemq/$ACTIVEMQ_VERSION/$ACTIVEMQ_ZIP_NAME"
$WINSW_DOWNLOAD_URL    = "https://github.com/winsw/winsw/releases/download/v$WINSW_VERSION/WinSW-x64.exe"

# Resolve paths relative to the scripts/ directory
$ScriptsDir   = $PSScriptRoot
$ProjectRoot  = (Resolve-Path (Join-Path $ScriptsDir "..")).Path
$InfraDir     = Join-Path $ProjectRoot "infrastructure"
$TomcatDir    = Join-Path $InfraDir "tomcat"
$ActiveMQDir  = Join-Path $InfraDir "activemq"
$WinSWDir     = Join-Path $ScriptsDir "winsw"
$ConfigDir    = Join-Path $ProjectRoot "config"
$DataDir      = Join-Path $ProjectRoot "data"

# ============================================================================
# Helper Functions
# ============================================================================

function Write-Header {
    param([string]$Message)
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor Cyan
    Write-Host ""
}

function Write-Step {
    param([string]$Message)
    Write-Host "[*] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[!] $Message" -ForegroundColor Yellow
}

function Write-Err {
    param([string]$Message)
    Write-Host "[X] $Message" -ForegroundColor Red
}

function Download-File {
    param(
        [string]$Url,
        [string]$OutputPath,
        [string]$Description
    )

    if (Test-Path $OutputPath) {
        Write-Step "$Description already downloaded. Skipping."
        return
    }

    Write-Step "Downloading $Description..."
    Write-Host "    URL: $Url"
    Write-Host "    Destination: $OutputPath"

    try {
        # Use TLS 1.2 for GitHub and Apache downloads
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

        $ProgressPreference = 'SilentlyContinue'  # Speed up Invoke-WebRequest
        Invoke-WebRequest -Uri $Url -OutFile $OutputPath -UseBasicParsing
        $ProgressPreference = 'Continue'

        $fileSize = (Get-Item $OutputPath).Length / 1MB
        Write-Step "$Description downloaded successfully ({0:N1} MB)" -f $fileSize
    }
    catch {
        Write-Err "Failed to download $Description from $Url"
        Write-Err $_.Exception.Message
        throw
    }
}

# ============================================================================
# Step 1: Check Prerequisites
# ============================================================================
Write-Header "Step 1: Checking Prerequisites"

# Check Java
try {
    $javaVersionOutput = & java -version 2>&1
    $javaVersionLine = ($javaVersionOutput | Select-String -Pattern '(java|openjdk) version').ToString()
    Write-Step "Java found: $javaVersionLine"

    # Extract major version number
    if ($javaVersionLine -match '"(\d+)[\._]') {
        $javaMajor = [int]$Matches[1]
        # Java 1.x format (e.g., "1.8.0") vs new format (e.g., "11.0.2")
        if ($javaMajor -eq 1) {
            if ($javaVersionLine -match '"1\.(\d+)') {
                $javaMajor = [int]$Matches[1]
            }
        }
        if ($javaMajor -lt 11) {
            Write-Err "Java 11 or higher is required. Found Java $javaMajor."
            Write-Err "Please install JDK 11+ and ensure it is in your PATH."
            exit 1
        }
        Write-Step "Java version $javaMajor meets minimum requirement (11+)."
    }
    else {
        Write-Warn "Could not parse Java version. Proceeding anyway..."
    }
}
catch {
    Write-Err "Java not found in PATH. Please install JDK 11+ and add it to PATH."
    exit 1
}

# Check JAVA_HOME
if (-not $env:JAVA_HOME) {
    Write-Warn "JAVA_HOME is not set. Some tools may require it."
    Write-Warn "Consider setting JAVA_HOME to your JDK installation directory."
}
else {
    Write-Step "JAVA_HOME is set to: $env:JAVA_HOME"
}

# Check Maven
try {
    $mvnVersionOutput = & mvn --version 2>&1 | Select-Object -First 1
    Write-Step "Maven found: $mvnVersionOutput"
}
catch {
    Write-Err "Maven not found in PATH. Please install Maven 3.6+ and add it to PATH."
    exit 1
}

Write-Step "All prerequisites met."

# ============================================================================
# Step 2: Create Directory Structure
# ============================================================================
Write-Header "Step 2: Creating Directory Structure"

$directories = @(
    $InfraDir,
    $TomcatDir,
    $ActiveMQDir,
    $WinSWDir,
    (Join-Path $DataDir "h2"),
    (Join-Path $DataDir "lucene-index"),
    (Join-Path $DataDir "documents")
)

foreach ($dir in $directories) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
        Write-Step "Created: $dir"
    }
    else {
        Write-Step "Exists:  $dir"
    }
}

# ============================================================================
# Step 3: Download Apache Tomcat
# ============================================================================
Write-Header "Step 3: Downloading Apache Tomcat $TOMCAT_VERSION"

$tomcatZipPath = Join-Path $TomcatDir $TOMCAT_ZIP_NAME
$tomcatExtractedDir = Join-Path $TomcatDir $TOMCAT_DIR_NAME

if (Test-Path (Join-Path $tomcatExtractedDir "bin")) {
    Write-Step "Tomcat already extracted. Skipping download and extraction."
}
else {
    Download-File -Url $TOMCAT_DOWNLOAD_URL -OutputPath $tomcatZipPath -Description "Apache Tomcat $TOMCAT_VERSION"

    Write-Step "Extracting Tomcat..."
    try {
        Expand-Archive -Path $tomcatZipPath -DestinationPath $TomcatDir -Force
        Write-Step "Tomcat extracted to $tomcatExtractedDir"
    }
    catch {
        Write-Err "Failed to extract Tomcat archive."
        Write-Err $_.Exception.Message
        throw
    }

    # Clean up zip
    if (Test-Path $tomcatZipPath) {
        Remove-Item $tomcatZipPath -Force
        Write-Step "Cleaned up Tomcat zip file."
    }
}

# ============================================================================
# Step 4: Download Apache ActiveMQ
# ============================================================================
Write-Header "Step 4: Downloading Apache ActiveMQ $ACTIVEMQ_VERSION"

$activemqZipPath = Join-Path $ActiveMQDir $ACTIVEMQ_ZIP_NAME
$activemqExtractedDir = Join-Path $ActiveMQDir $ACTIVEMQ_DIR_NAME

if (Test-Path (Join-Path $activemqExtractedDir "bin")) {
    Write-Step "ActiveMQ already extracted. Skipping download and extraction."
}
else {
    Download-File -Url $ACTIVEMQ_DOWNLOAD_URL -OutputPath $activemqZipPath -Description "Apache ActiveMQ $ACTIVEMQ_VERSION"

    Write-Step "Extracting ActiveMQ..."
    try {
        Expand-Archive -Path $activemqZipPath -DestinationPath $ActiveMQDir -Force
        Write-Step "ActiveMQ extracted to $activemqExtractedDir"
    }
    catch {
        Write-Err "Failed to extract ActiveMQ archive."
        Write-Err $_.Exception.Message
        throw
    }

    # Clean up zip
    if (Test-Path $activemqZipPath) {
        Remove-Item $activemqZipPath -Force
        Write-Step "Cleaned up ActiveMQ zip file."
    }
}

# ============================================================================
# Step 5: Download WinSW
# ============================================================================
Write-Header "Step 5: Downloading WinSW v$WINSW_VERSION"

$winswExePath = Join-Path $WinSWDir "WinSW-x64.exe"

Download-File -Url $WINSW_DOWNLOAD_URL -OutputPath $winswExePath -Description "WinSW v$WINSW_VERSION"

# ============================================================================
# Step 6: Build Maven Project
# ============================================================================
Write-Header "Step 6: Building Maven Project"

Write-Step "Running: mvn clean package -DskipTests"
Write-Step "Project root: $ProjectRoot"

try {
    Push-Location $ProjectRoot
    & mvn clean package -DskipTests 2>&1 | ForEach-Object { Write-Host "    $_" }
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Maven build failed with exit code $LASTEXITCODE"
        Pop-Location
        exit 1
    }
    Write-Step "Maven build completed successfully."
    Pop-Location
}
catch {
    Write-Err "Maven build failed."
    Write-Err $_.Exception.Message
    Pop-Location
    throw
}

# ============================================================================
# Step 7: Deploy WAR Files to Tomcat
# ============================================================================
Write-Header "Step 7: Deploying WAR Files to Tomcat"

$tomcatWebapps = Join-Path $tomcatExtractedDir "webapps"

$warFiles = @(
    @{ Source = Join-Path $ProjectRoot "docmgr-webapp\target\docmgr.war"; Name = "docmgr.war" },
    @{ Source = Join-Path $ProjectRoot "auth-service\target\auth.war";    Name = "auth.war" },
    @{ Source = Join-Path $ProjectRoot "rest-api\target\api.war";         Name = "api.war" }
)

foreach ($war in $warFiles) {
    if (Test-Path $war.Source) {
        Copy-Item -Path $war.Source -Destination (Join-Path $tomcatWebapps $war.Name) -Force
        Write-Step "Deployed $($war.Name) to Tomcat webapps."
    }
    else {
        Write-Err "WAR file not found: $($war.Source)"
        Write-Err "Check Maven build output for errors."
        exit 1
    }
}

# ============================================================================
# Step 8: Copy Custom Configuration Files
# ============================================================================
Write-Header "Step 8: Copying Configuration Files"

# Copy custom Tomcat server.xml
$customServerXml = Join-Path $ConfigDir "tomcat-server.xml"
$tomcatConfDir = Join-Path $tomcatExtractedDir "conf"

if (Test-Path $customServerXml) {
    Copy-Item -Path $customServerXml -Destination (Join-Path $tomcatConfDir "server.xml") -Force
    Write-Step "Copied custom server.xml to Tomcat conf."
}
else {
    Write-Warn "Custom tomcat-server.xml not found at $customServerXml. Using Tomcat defaults."
}

# Copy custom ActiveMQ configuration
$customActivemqXml = Join-Path $ConfigDir "activemq.xml"
$activemqConfDir = Join-Path $activemqExtractedDir "conf"

if (Test-Path $customActivemqXml) {
    Copy-Item -Path $customActivemqXml -Destination (Join-Path $activemqConfDir "activemq.xml") -Force
    Write-Step "Copied custom activemq.xml to ActiveMQ conf."
}
else {
    Write-Warn "Custom activemq.xml not found at $customActivemqXml. Using ActiveMQ defaults."
}

# ============================================================================
# Summary
# ============================================================================
Write-Header "Setup Complete!"

Write-Host "  Project Root:     $ProjectRoot" -ForegroundColor White
Write-Host "  Tomcat Home:      $tomcatExtractedDir" -ForegroundColor White
Write-Host "  ActiveMQ Home:    $activemqExtractedDir" -ForegroundColor White
Write-Host "  WinSW Executable: $winswExePath" -ForegroundColor White
Write-Host "  Data Directory:   $DataDir" -ForegroundColor White
Write-Host ""
Write-Host "  WAR Files Deployed:" -ForegroundColor White
Write-Host "    - docmgr.war  (JSF + PrimeFaces Document Manager)" -ForegroundColor Gray
Write-Host "    - auth.war    (Spring Security Auth Service)" -ForegroundColor Gray
Write-Host "    - api.war     (JAX-RS / Jersey REST API)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Standalone JARs Built:" -ForegroundColor White
Write-Host "    - document-processor/target/document-processor-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host "    - tika-processor/target/tika-processor-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host "    - lucene-search/target/lucene-search-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host "    - batch-runner/target/batch-runner-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host "    - swing-client/target/swing-client-1.0-SNAPSHOT.jar" -ForegroundColor Gray
Write-Host ""
Write-Host "  Next Steps:" -ForegroundColor Yellow
Write-Host "    1. Run install-services.ps1 as Administrator to install Windows services" -ForegroundColor Yellow
Write-Host "    2. Run start-all.bat to start all services" -ForegroundColor Yellow
Write-Host "    3. Or run start-all-no-services.bat for direct java.exe process testing" -ForegroundColor Yellow
Write-Host ""
