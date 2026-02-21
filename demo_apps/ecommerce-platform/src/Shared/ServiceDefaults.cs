using Microsoft.Extensions.Logging;

namespace EcommercePlatform.Shared;

/// <summary>
/// Logs Datadog SSI environment variables on startup for validation.
/// </summary>
public static class ServiceDefaults
{
    public static void LogDatadogConfig(ILogger logger, string serviceName)
    {
        var ddService = Environment.GetEnvironmentVariable("DD_SERVICE") ?? "(not set)";
        var ddEnv = Environment.GetEnvironmentVariable("DD_ENV") ?? "(not set)";
        var ddVersion = Environment.GetEnvironmentVariable("DD_VERSION") ?? "(not set)";
        var ddTraceEnabled = Environment.GetEnvironmentVariable("DD_TRACE_ENABLED") ?? "(not set)";
        var ddAgentHost = Environment.GetEnvironmentVariable("DD_AGENT_HOST") ?? "(not set)";
        var corClrProfiler = Environment.GetEnvironmentVariable("COR_PROFILER") ?? "(not set)";
        var coreClrProfiler = Environment.GetEnvironmentVariable("CORECLR_PROFILER") ?? "(not set)";

        logger.LogInformation("=== Datadog SSI Configuration for {ServiceName} ===", serviceName);
        logger.LogInformation("  DD_SERVICE:        {DdService}", ddService);
        logger.LogInformation("  DD_ENV:            {DdEnv}", ddEnv);
        logger.LogInformation("  DD_VERSION:        {DdVersion}", ddVersion);
        logger.LogInformation("  DD_TRACE_ENABLED:  {DdTraceEnabled}", ddTraceEnabled);
        logger.LogInformation("  DD_AGENT_HOST:     {DdAgentHost}", ddAgentHost);
        logger.LogInformation("  COR_PROFILER:      {CorProfiler}", corClrProfiler);
        logger.LogInformation("  CORECLR_PROFILER:  {CoreClrProfiler}", coreClrProfiler);
        logger.LogInformation("  Process ID:        {ProcessId}", Environment.ProcessId);
        logger.LogInformation("  Process Path:      {ProcessPath}", Environment.ProcessPath);
        logger.LogInformation("================================================");
    }
}
