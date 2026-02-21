using EcommercePlatform.NotificationWorker;
using EcommercePlatform.NotificationWorker.Data;
using EcommercePlatform.Shared;
using EcommercePlatform.Shared.Messaging;
using Microsoft.EntityFrameworkCore;

var host = Host.CreateDefaultBuilder(args)
    .UseWindowsService()
    .ConfigureServices((context, services) =>
    {
        var config = context.Configuration;
        var queueBasePath = config["QueueBasePath"] ?? @"C:\DemoApps\ecommerce\queues";
        var databasePath = config["DatabasePath"] ?? @"C:\DemoApps\ecommerce\data\notifications.db";

        // Ensure database directory exists
        var dbDirectory = Path.GetDirectoryName(databasePath);
        if (!string.IsNullOrEmpty(dbDirectory))
        {
            Directory.CreateDirectory(dbDirectory);
        }

        // Register FileMessageQueue for reading fulfillment-events
        services.AddSingleton(sp =>
        {
            var logger = sp.GetRequiredService<ILogger<FileMessageQueue>>();
            return new FileMessageQueue(queueBasePath, "fulfillment-events", logger);
        });

        // Register NotificationDbContext with SQLite
        services.AddDbContext<NotificationDbContext>(options =>
        {
            options.UseSqlite($"Data Source={databasePath}");
        });

        services.AddHostedService<Worker>();
    })
    .Build();

// Log Datadog SSI configuration on startup
var logger = host.Services.GetRequiredService<ILogger<Program>>();
ServiceDefaults.LogDatadogConfig(logger, "NotificationWorker");

// Ensure database is created
using (var scope = host.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<NotificationDbContext>();
    db.Database.EnsureCreated();
}

host.Run();
