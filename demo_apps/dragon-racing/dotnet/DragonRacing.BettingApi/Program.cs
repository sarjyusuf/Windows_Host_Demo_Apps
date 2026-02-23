using DragonRacing.Shared;
using DragonRacing.BettingApi.Data;

BannerHelper.PrintBettingApiBanner();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();

builder.Services.AddSingleton<BetDatabase>();

builder.Services.AddHttpClient("RaceSchedule", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:RaceSchedule"] ?? "http://localhost:9081");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.Services.AddHttpClient("DragonStable", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:DragonStable"] ?? "http://localhost:9080");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.WebHost.UseUrls("http://0.0.0.0:5001");

var app = builder.Build();

// Initialize database on startup
var db = app.Services.GetRequiredService<BetDatabase>();
db.Initialize();

app.UseRouting();
app.MapControllers();

app.MapGet("/health", () => Results.Ok(new
{
    Service = "DragonRacing.BettingApi",
    Status = "Healthy",
    Timestamp = DateTime.UtcNow
}));

Console.ForegroundColor = ConsoleColor.Green;
Console.WriteLine("[BettingApi] SQLite database initialized. Ready to accept bets!");
Console.ResetColor();

app.Run();
