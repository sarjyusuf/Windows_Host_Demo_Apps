using DragonRacing.Shared;

BannerHelper.PrintPortalBanner();

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddRazorPages();

builder.Services.AddHttpClient("DragonStable", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:DragonStable"] ?? "http://localhost:9080");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.Services.AddHttpClient("RaceSchedule", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:RaceSchedule"] ?? "http://localhost:9081");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.Services.AddHttpClient("Leaderboard", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:Leaderboard"] ?? "http://localhost:9082");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.Services.AddHttpClient("BettingApi", client =>
{
    client.BaseAddress = new Uri(builder.Configuration["ServiceUrls:BettingApi"] ?? "http://localhost:5001");
    client.Timeout = TimeSpan.FromSeconds(10);
});

builder.WebHost.UseUrls("http://0.0.0.0:5000");

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Error");
}

app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();
app.MapRazorPages();

app.MapGet("/health", () => Results.Ok(new
{
    Service = "DragonRacing.Portal",
    Status = "Healthy",
    Timestamp = DateTime.UtcNow
}));

app.Run();
