using Microsoft.EntityFrameworkCore;
using EcommercePlatform.InventoryApi.Data;
using EcommercePlatform.Shared;

var builder = WebApplication.CreateBuilder(args);

// Add controllers
builder.Services.AddControllers();

// Configure EF Core with SQLite
var dbPath = builder.Configuration.GetValue<string>("DatabasePath")
    ?? Path.Combine(AppContext.BaseDirectory, "inventory.db");

// Ensure the directory for the database file exists
var dbDirectory = Path.GetDirectoryName(dbPath);
if (!string.IsNullOrEmpty(dbDirectory))
{
    Directory.CreateDirectory(dbDirectory);
}

builder.Services.AddDbContext<InventoryDbContext>(options =>
    options.UseSqlite($"Data Source={dbPath}"));

// CORS for frontend on WebStorefront
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader());
});

var app = builder.Build();

app.UseCors();

// Log Datadog SSI configuration on startup
var startupLogger = app.Services.GetRequiredService<ILogger<Program>>();
ServiceDefaults.LogDatadogConfig(startupLogger, "InventoryApi");
startupLogger.LogInformation("InventoryApi starting on port 5102");
startupLogger.LogInformation("Database path: {DatabasePath}", dbPath);

// Ensure database is created with seed data
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<InventoryDbContext>();
    db.Database.EnsureCreated();
    startupLogger.LogInformation("Inventory database initialized. Items: {Count}",
        db.InventoryItems.Count());
}

app.MapControllers();

app.Run();
