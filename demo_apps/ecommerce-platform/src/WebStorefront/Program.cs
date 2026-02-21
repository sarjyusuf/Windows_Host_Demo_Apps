using Microsoft.EntityFrameworkCore;
using EcommercePlatform.Shared;
using EcommercePlatform.WebStorefront.Data;

var builder = WebApplication.CreateBuilder(args);

// -- EF Core with SQLite --
var dbPath = builder.Configuration.GetValue<string>("DatabasePath") ?? "storefront.db";
builder.Services.AddDbContext<StorefrontDbContext>(options =>
    options.UseSqlite($"Data Source={dbPath}"));

// -- Memory cache for shopping carts --
builder.Services.AddMemoryCache();

// -- Named HttpClients via IHttpClientFactory --
var orderApiUrl = builder.Configuration.GetValue<string>("OrderApiUrl") ?? "http://localhost:5101";
var inventoryApiUrl = builder.Configuration.GetValue<string>("InventoryApiUrl") ?? "http://localhost:5102";

builder.Services.AddHttpClient("OrderApi", client =>
{
    client.BaseAddress = new Uri(orderApiUrl);
    client.DefaultRequestHeaders.Add("Accept", "application/json");
    client.Timeout = TimeSpan.FromSeconds(30);
});

builder.Services.AddHttpClient("InventoryApi", client =>
{
    client.BaseAddress = new Uri(inventoryApiUrl);
    client.DefaultRequestHeaders.Add("Accept", "application/json");
    client.Timeout = TimeSpan.FromSeconds(30);
});

// -- MVC controllers --
builder.Services.AddControllers();

var app = builder.Build();

// -- Ensure database is created and seeded --
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<StorefrontDbContext>();
    db.Database.EnsureCreated();
}

// -- Log Datadog SSI environment configuration --
var startupLogger = app.Services.GetRequiredService<ILoggerFactory>().CreateLogger("WebStorefront");
ServiceDefaults.LogDatadogConfig(startupLogger, "WebStorefront");
startupLogger.LogInformation("WebStorefront listening on port 5100");
startupLogger.LogInformation("  OrderApi URL:     {OrderApiUrl}", orderApiUrl);
startupLogger.LogInformation("  InventoryApi URL: {InventoryApiUrl}", inventoryApiUrl);
startupLogger.LogInformation("  Database path:    {DatabasePath}", dbPath);

app.UseDefaultFiles();
app.UseStaticFiles();
app.MapControllers();

app.Run();
