using DragonRacing.Shared;
using DragonRacing.RaceSimulator;

BannerHelper.PrintRaceSimulatorBanner();

var builder = Host.CreateApplicationBuilder(args);

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

builder.Services.AddHostedService<RaceSimulatorWorker>();

// Ensure queue directories exist
QueueHelper.EnsureDirectoriesExist();

var host = builder.Build();

Console.ForegroundColor = ConsoleColor.Cyan;
Console.WriteLine("[RaceSimulator] Queue directories initialized:");
Console.WriteLine($"  Races Pending:    {QueueHelper.RacesPendingDir}");
Console.WriteLine($"  Races Completed:  {QueueHelper.RacesCompletedDir}");
Console.WriteLine($"  Payouts Pending:  {QueueHelper.PayoutsPendingDir}");
Console.WriteLine($"  Payouts Completed: {QueueHelper.PayoutsCompletedDir}");
Console.ResetColor();
Console.WriteLine();

host.Run();
