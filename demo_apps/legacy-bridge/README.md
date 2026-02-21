# LegacyBridge - Enterprise Document Management System

## Overview

LegacyBridge is a multi-module Java enterprise application designed to simulate a realistic document management system. Its primary purpose is to serve as a **test bed for Single Step Instrumentation (SSI) on Windows**. The application runs as **7 separate JVM processes**, each using a different combination of Java frameworks and Windows startup methods (Apache Commons Daemon/prunsrv, WinSW service wrappers, direct `java -jar`, and batch scripts).

This diversity of process creation patterns is intentional -- it exercises the various code paths that an SSI driver (such as `ddinjector`) must handle when instrumenting JVM processes on Windows.

## Architecture

```
+------------------------------------------------------------------+
|                        Windows Host                               |
|                                                                   |
|  +------------------------------------------------------------+  |
|  |              Apache Tomcat 9 (port 8080)                    |  |
|  |  Startup: prunsrv.exe (Apache Commons Daemon) OR            |  |
|  |           catalina.bat (no-services mode)                   |  |
|  |                                                             |  |
|  |  +----------------+ +---------------+ +----------------+    |  |
|  |  | docmgr.war     | | auth.war      | | api.war        |   |  |
|  |  | JSF/PrimeFaces | | Spring        | | JAX-RS/Jersey  |   |  |
|  |  | Document UI    | | Security Auth | | REST endpoints |   |  |
|  |  +----------------+ +---------------+ +-------+--------+   |  |
|  +------------------------------------------------------------+  |
|                                              |                    |
|           +----------------------------------+-------+            |
|           |              |                   |       |            |
|           v              v                   v       v            |
|  +----------------+ +------------------+ +------------------+     |
|  | Tika Processor | | Lucene Search    | | ActiveMQ 5.18    |    |
|  | port 8081      | | port 8082        | | port 61616 (JMS) |    |
|  | Shade JAR      | | Shade JAR        | | port 8161 (Web)  |    |
|  | java -jar      | | java -jar        | | activemq service |    |
|  +----------------+ +------------------+ +------------------+     |
|           ^              ^                   |                    |
|           |              |                   |                    |
|  +---------------------------------------------+                 |
|  | Document Processor      | port 8083          |                |
|  | Spring Boot + JMS       | WinSW service      |                |
|  | Listens: document.process queue              |                 |
|  | Calls Tika, Lucene, REST API                 |                 |
|  +---------------------------------------------+                 |
|                                                                   |
|  +---------------------+   +---------------------+               |
|  | Batch Runner         |   | Swing Client        |              |
|  | Quartz Scheduler     |   | Desktop GUI         |              |
|  | WinSW service        |   | java -jar           |              |
|  | Shade JAR            |   | Shade JAR           |              |
|  +---------------------+   +---------------------+               |
+------------------------------------------------------------------+
```

### Data Flow

1. **Swing Client** uploads documents via the **REST API** (api.war on Tomcat)
2. REST API stores metadata in **H2 database** and sends a message to **ActiveMQ** queue `document.process`
3. **Document Processor** picks up the JMS message and orchestrates:
   - Calls **Tika Processor** (HTTP) for text extraction
   - Calls **Lucene Search** (HTTP) for full-text indexing
   - Updates document status via **REST API**
4. **Batch Runner** periodically triggers reindexing jobs and maintenance tasks
5. **Auth Service** (auth.war) handles user authentication via Spring Security
6. **Document Manager** (docmgr.war) provides a JSF/PrimeFaces web UI

## Prerequisites

- **Java 11+** (JDK, not JRE) -- must be in PATH
- **Maven 3.6+** -- must be in PATH
- **Windows** host (Windows 10/11 or Windows Server 2016+)
- **PowerShell 5.0+** (included with Windows 10+)
- **Administrator privileges** (for installing Windows services)
- Internet access (for downloading Tomcat, ActiveMQ, WinSW during setup)

## Quick Start

### 1. Run Setup

Open PowerShell and run:

```powershell
cd legacy-bridge\scripts
.\setup.ps1
```

This will:
- Validate Java and Maven installations
- Download Apache Tomcat 9.0.84
- Download Apache ActiveMQ 5.18.3
- Download WinSW v2.12.0
- Build all 8 Maven modules
- Deploy WAR files to Tomcat
- Copy configuration files
- Create data directories

### 2a. Start with Windows Services (recommended for production-like testing)

Install services (requires Administrator PowerShell):

```powershell
.\install-services.ps1
```

Then start everything:

```batch
start-all.bat
```

### 2b. Start without Windows Services (recommended for SSI injection testing)

```batch
start-all-no-services.bat
```

This launches all 7 JVM processes directly via `java -jar` and native startup scripts. Each process runs in its own console window. This mode is preferred for SSI testing because it creates `java.exe` processes directly, providing the most straightforward injection scenario.

### 3. Verify Health

```batch
check-health.bat
```

### 4. Stop Everything

```batch
stop-all.bat
REM or
stop-all-no-services.bat
```

### 5. Uninstall Services (if installed)

```powershell
.\uninstall-services.ps1
```

## Service Details

| # | Process | Port | Framework | Artifact | Startup Method |
|---|---------|------|-----------|----------|----------------|
| 1 | **Tomcat** (docmgr + auth + api) | 8080 | JSF/PrimeFaces, Spring Security, JAX-RS/Jersey | docmgr.war, auth.war, api.war | prunsrv.exe (Commons Daemon) |
| 2 | **Tika Processor** | 8081 | Apache Tika + embedded HTTP server | tika-processor-1.0-SNAPSHOT.jar | java -jar (direct) |
| 3 | **Lucene Search** | 8082 | Apache Lucene + embedded HTTP server | lucene-search-1.0-SNAPSHOT.jar | java -jar (direct) |
| 4 | **Document Processor** | 8083 | Spring Boot + JMS/ActiveMQ | document-processor-1.0-SNAPSHOT.jar | WinSW service wrapper |
| 5 | **Batch Runner** | -- | Quartz Scheduler | batch-runner-1.0-SNAPSHOT.jar | WinSW service wrapper |
| 6 | **ActiveMQ** | 61616, 8161 | Apache ActiveMQ broker | (bundled distribution) | ActiveMQ built-in service |
| 7 | **Swing Client** | -- | Java Swing + FlatLaf | swing-client-1.0-SNAPSHOT.jar | java -jar (direct) |

## Ports

| Port | Service | Protocol |
|------|---------|----------|
| 8080 | Tomcat (docmgr, auth, api WARs) | HTTP |
| 8081 | Tika Processor | HTTP |
| 8082 | Lucene Search | HTTP |
| 8083 | Document Processor (Spring Boot) | HTTP |
| 8161 | ActiveMQ Web Console | HTTP |
| 61616 | ActiveMQ Broker | OpenWire/TCP |
| 8005 | Tomcat Shutdown | TCP (localhost only) |

## SSI Testing Guide

### Overview

Single Step Instrumentation (SSI) on Windows works by hooking into process creation at the OS level. When a new `java.exe` process is created, the SSI driver (e.g., `ddinjector`) injects a Java agent (e.g., `dd-java-agent.jar`) into the JVM by setting `JAVA_TOOL_OPTIONS` or modifying the command line before the JVM fully starts.

LegacyBridge tests this by creating JVM processes through multiple different mechanisms:

1. **prunsrv.exe** (Apache Commons Daemon) -- creates a JVM in-process using JNI, does NOT spawn `java.exe`
2. **WinSW** -- spawns `java.exe` as a child of `svchost.exe` (service host)
3. **Direct java -jar** -- spawns `java.exe` directly from `cmd.exe`
4. **catalina.bat** -- spawns `java.exe` from a batch script chain
5. **activemq.bat** -- spawns `java.exe` via the ActiveMQ wrapper script

### Verifying Injection

#### Method 1: Check JAVA_TOOL_OPTIONS

If SSI sets `JAVA_TOOL_OPTIONS`, each Java process should log it at startup:

```
Picked up JAVA_TOOL_OPTIONS: -javaagent:C:\path\to\dd-java-agent.jar
```

Check each console window or service log for this message.

#### Method 2: Process Explorer

1. Download and run [Process Explorer](https://learn.microsoft.com/en-us/sysinternals/downloads/process-explorer) from Sysinternals
2. Find each `java.exe` process in the process tree
3. Right-click and select "Properties"
4. Check the "Environment" tab for `JAVA_TOOL_OPTIONS` or `DD_*` variables
5. Check the "Command Line" for `-javaagent:` arguments

#### Method 3: JMX/MBean

If the agent supports it, connect via JConsole or VisualVM and check for agent-specific MBeans.

### Which Processes Should Be Injected

| Process | Startup Method | Expected Injection |
|---------|---------------|-------------------|
| Tomcat (via prunsrv) | JNI in-process JVM | Depends on driver -- prunsrv does NOT spawn java.exe |
| Tomcat (via catalina.bat) | java.exe from bat | YES -- direct java.exe creation |
| Tika Processor | java -jar | YES -- direct java.exe creation |
| Lucene Search | java -jar | YES -- direct java.exe creation |
| Document Processor (WinSW) | java.exe from service | YES -- java.exe spawned by service host |
| Batch Runner (WinSW) | java.exe from service | YES -- java.exe spawned by service host |
| ActiveMQ | java.exe from bat | YES -- java.exe creation via wrapper |
| Swing Client | java -jar | YES -- direct java.exe creation |

**Key insight**: When Tomcat is installed as a service via `prunsrv.exe`, it creates the JVM in-process using JNI and does NOT spawn a separate `java.exe`. This means process-creation-based injection may not work. Use `start-all-no-services.bat` to test with `catalina.bat run` instead, which does spawn `java.exe`.

### Using start-all-no-services.bat for Direct Injection Testing

The `start-all-no-services.bat` script is specifically designed for SSI testing:

- Every JVM process is started as a direct `java.exe` invocation
- No service wrappers (prunsrv, WinSW, svchost) are involved
- Each process runs in its own visible console window
- You can immediately see `JAVA_TOOL_OPTIONS` pickup messages in each console
- Use Process Explorer to inspect the process tree and verify injection on each process

This is the recommended mode for initial SSI validation. Once direct injection is confirmed working, switch to `start-all.bat` (service mode) to test injection through service wrappers.

### Checking Injection via Environment Variables

From an elevated PowerShell prompt, you can inspect the environment of running Java processes:

```powershell
# List all java.exe processes and their command lines
Get-WmiObject Win32_Process -Filter "name='java.exe'" |
    Select-Object ProcessId, CommandLine |
    Format-Table -AutoSize -Wrap

# Check a specific process's environment (requires admin)
# Replace <PID> with the actual process ID
(Get-Process -Id <PID>).StartInfo.EnvironmentVariables
```

Or using `wmic`:

```batch
wmic process where "name='java.exe'" get ProcessId,CommandLine
```

## Project Structure

```
legacy-bridge/
  pom.xml                          # Parent POM (8 modules)
  config/
    tomcat-server.xml              # Custom Tomcat configuration
    activemq.xml                   # Custom ActiveMQ configuration
  scripts/
    setup.ps1                      # One-time setup (download, build, deploy)
    install-services.ps1           # Install Windows services (admin)
    uninstall-services.ps1         # Remove Windows services (admin)
    start-all.bat                  # Start with services
    start-all-no-services.bat      # Start all directly (SSI testing)
    stop-all.bat                   # Stop services
    stop-all-no-services.bat       # Kill all direct processes
    check-health.bat               # Verify all endpoints
    winsw/
      WinSW-x64.exe               # WinSW binary (downloaded by setup)
      document-processor-service.xml  # WinSW config for Document Processor
      batch-runner-service.xml        # WinSW config for Batch Runner
  docmgr-webapp/                   # JSF + PrimeFaces web UI (WAR)
  auth-service/                    # Spring Security auth (WAR)
  rest-api/                        # JAX-RS / Jersey REST API (WAR)
  document-processor/              # Spring Boot + JMS processor (JAR)
  tika-processor/                  # Apache Tika parser service (JAR)
  lucene-search/                   # Lucene search service (JAR)
  batch-runner/                    # Quartz scheduler (JAR)
  swing-client/                    # Java Swing desktop GUI (JAR)
  infrastructure/                  # Created by setup.ps1
    tomcat/
      apache-tomcat-9.0.84/
    activemq/
      apache-activemq-5.18.3/
  data/                            # Created by setup.ps1
    h2/                            # H2 database files
    lucene-index/                  # Lucene index files
    documents/                     # Uploaded document storage
```

## Troubleshooting

### Setup Issues

**"Java not found in PATH"**
- Ensure JDK 11+ is installed (not just JRE)
- Run `java -version` in a new terminal to verify
- Add `JAVA_HOME\bin` to your PATH if needed

**"Maven not found in PATH"**
- Download Maven from https://maven.apache.org/download.cgi
- Add Maven's `bin` directory to PATH
- Run `mvn --version` to verify

**Maven build fails**
- Check that `JAVA_HOME` points to a JDK (not JRE)
- Run `mvn clean package -DskipTests -X` for debug output
- Check if any dependencies fail to download (proxy/firewall issues)

**Download fails (Tomcat/ActiveMQ/WinSW)**
- Check internet connectivity
- If behind a corporate proxy, configure PowerShell proxy settings
- Try downloading manually and placing files in the correct directories

### Startup Issues

**Port already in use**
- Check which process is using the port: `netstat -aon | findstr :<port>`
- Kill the conflicting process or change the port in the configuration
- Common conflict: another Tomcat on 8080 or another broker on 61616

**Tomcat starts but WARs fail to deploy**
- Check `infrastructure\tomcat\apache-tomcat-*\logs\catalina.*.log`
- Check `localhost.*.log` for individual WAR deployment errors
- Ensure WAR files are present in `webapps/`

**ActiveMQ fails to start**
- Check `infrastructure\activemq\apache-activemq-*\data\activemq.log`
- Port 61616 may be in use by another broker instance
- Ensure `JAVA_HOME` is set if ActiveMQ cannot find Java

**Document Processor fails to connect to ActiveMQ**
- Ensure ActiveMQ is fully started before starting the Document Processor
- Check ActiveMQ credentials match (default: admin/admin)
- The `start-all.bat` script includes a 10-second wait for ActiveMQ

**WinSW service fails to install**
- Ensure you are running PowerShell as Administrator
- Check that the XML config file matches the service exe name
- Look for WinSW logs in `scripts\winsw\` directory

### SSI-Specific Issues

**Injection not happening**
- Verify the SSI driver is installed and running
- Check if `JAVA_TOOL_OPTIONS` is being set globally
- Try `start-all-no-services.bat` to rule out service wrapper issues
- prunsrv.exe (Tomcat service) does NOT spawn java.exe -- injection may not apply

**Agent causes startup failure**
- Check the agent JAR path is valid and the file exists
- Verify agent compatibility with Java 11
- Check for agent version mismatches
- Remove the agent temporarily to confirm the application starts without it

**Partial injection (some processes injected, others not)**
- Different startup methods create JVM processes differently
- prunsrv uses JNI (no java.exe), WinSW spawns java.exe, direct jar uses java.exe
- The SSI driver may need different hooks for different creation methods
- Use Process Explorer to compare the process trees of injected vs non-injected processes
