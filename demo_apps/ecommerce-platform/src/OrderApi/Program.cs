using System.Text.Json.Serialization;
using Microsoft.EntityFrameworkCore;
using EcommercePlatform.OrderApi.Data;
using EcommercePlatform.Shared;
using EcommercePlatform.Shared.Messaging;

var builder = WebApplication.CreateBuilder(args);

// Add controllers with JSON enum string converter for clean API
builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
    });

// Configure SQLite database
var databasePath = builder.Configuration.GetValue<string>("DatabasePath")
    ?? Path.Combine(AppContext.BaseDirectory, "orders.db");

// Ensure the directory for the database file exists
var dbDirectory = Path.GetDirectoryName(databasePath);
if (!string.IsNullOrEmpty(dbDirectory))
{
    Directory.CreateDirectory(dbDirectory);
}

builder.Services.AddDbContext<OrderDbContext>(options =>
    options.UseSqlite($"Data Source={databasePath}"));

// Configure FileMessageQueue as a singleton for publishing order events
var queueBasePath = builder.Configuration.GetValue<string>("QueueBasePath")
    ?? @"C:\DemoApps\ecommerce\queues";

builder.Services.AddSingleton<FileMessageQueue>(sp =>
{
    var logger = sp.GetRequiredService<ILoggerFactory>().CreateLogger<FileMessageQueue>();
    return new FileMessageQueue(queueBasePath, "order-events", logger);
});

// CORS for frontend on WebStorefront
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader());
});

var app = builder.Build();

app.UseCors();

// Ensure the database is created on startup
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<OrderDbContext>();
    db.Database.EnsureCreated();
}

// Log Datadog SSI configuration on startup
var startupLogger = app.Services.GetRequiredService<ILoggerFactory>().CreateLogger("Startup");
ServiceDefaults.LogDatadogConfig(startupLogger, "OrderApi");

app.MapControllers();

app.Run();
