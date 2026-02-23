using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text;
using System.Text.Json;

namespace DragonRacing.RaceSimulator;

public class RaceSimulatorWorker : BackgroundService
{
    private readonly ILogger<RaceSimulatorWorker> _logger;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly Random _random = new();
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true, WriteIndented = true };

    private static readonly string[] CommentaryVerbs = {
        "surges forward", "takes the lead", "dives through the air", "breathes fire and accelerates",
        "weaves between obstacles", "gains ground rapidly", "unleashes a burst of speed",
        "roars with determination", "charges ahead", "darts past a rival"
    };

    private static readonly string[] CommentaryMidRace = {
        "is neck and neck with", "pulls ahead of", "falls behind", "battles fiercely with",
        "outmaneuvers", "closes the gap on", "challenges"
    };

    public RaceSimulatorWorker(ILogger<RaceSimulatorWorker> logger, IHttpClientFactory httpClientFactory)
    {
        _logger = logger;
        _httpClientFactory = httpClientFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        Console.ForegroundColor = ConsoleColor.Cyan;
        Console.WriteLine("[RaceSimulator] Worker started. Watching for pending race files...");
        Console.ResetColor();

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                var pendingFiles = QueueHelper.GetPendingRaceFiles();

                foreach (var file in pendingFiles)
                {
                    if (stoppingToken.IsCancellationRequested) break;

                    Console.ForegroundColor = ConsoleColor.Yellow;
                    Console.WriteLine($"\n[RaceSimulator] Found pending race file: {Path.GetFileName(file)}");
                    Console.ResetColor();

                    var race = QueueHelper.ReadJsonFile<Race>(file);
                    if (race == null)
                    {
                        Console.ForegroundColor = ConsoleColor.Red;
                        Console.WriteLine($"[RaceSimulator] Could not read race file: {file}");
                        Console.ResetColor();
                        continue;
                    }

                    await SimulateRace(race, stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error in race simulator loop");
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine($"[RaceSimulator] Error: {ex.Message}");
                Console.ResetColor();
            }

            await Task.Delay(3000, stoppingToken);
        }
    }

    private async Task SimulateRace(Race race, CancellationToken ct)
    {
        Console.ForegroundColor = ConsoleColor.Cyan;
        Console.WriteLine($"\n{'=',-60}");
        Console.WriteLine($"  RACE #{race.Id} - {race.TrackName}");
        Console.WriteLine($"  {race.Entries.Count} DRAGONS READY TO RACE!");
        Console.WriteLine($"{'=',-60}");
        Console.ResetColor();

        // Get dragon stats
        var dragons = await GetDragonStats(race.Entries);

        // Starting line
        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine("\n  The dragons line up at the starting gate...");
        await Task.Delay(1000, ct);
        Console.WriteLine("  3...");
        await Task.Delay(800, ct);
        Console.WriteLine("  2...");
        await Task.Delay(800, ct);
        Console.WriteLine("  1...");
        await Task.Delay(800, ct);
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine("  THEY'RE OFF! The race has begun!\n");
        Console.ResetColor();

        // Simulate race for each dragon
        var results = new List<RaceResult>();

        foreach (var entry in race.Entries)
        {
            var dragon = dragons.FirstOrDefault(d => d.Id == entry.DragonId);
            double speed = dragon?.Speed ?? 80;
            double stamina = dragon?.Stamina ?? 80;
            double agility = dragon?.Agility ?? 80;

            // Calculate finish time: lower is better
            double speedFactor = speed * RandomRange(0.8, 1.2);
            double staminaFactor = stamina * RandomRange(0.9, 1.1);
            double agilityFactor = agility * RandomRange(0.85, 1.15);

            // Invert so higher stats = lower time
            double finishTime = 300.0 / (speedFactor + staminaFactor + agilityFactor) * 100.0;
            finishTime = Math.Round(finishTime, 2);

            results.Add(new RaceResult
            {
                DragonId = entry.DragonId,
                DragonName = entry.DragonName,
                FinishTime = finishTime
            });
        }

        // Sort by finish time and assign positions
        results = results.OrderBy(r => r.FinishTime).ToList();
        for (int i = 0; i < results.Count; i++)
        {
            results[i].Position = i + 1;
        }

        // Commentary during race
        var shuffled = results.OrderBy(_ => _random.Next()).ToList();
        for (int i = 0; i < Math.Min(shuffled.Count, 4); i++)
        {
            await Task.Delay(700, ct);
            var dr = shuffled[i];
            var verb = CommentaryVerbs[_random.Next(CommentaryVerbs.Length)];

            Console.ForegroundColor = (ConsoleColor)(i % 6 + 9); // Vary colors
            Console.WriteLine($"  {GetDragonEmoji()} {dr.DragonName} {verb}!");
            Console.ResetColor();
        }

        // Mid-race commentary
        if (results.Count >= 2)
        {
            await Task.Delay(800, ct);
            var midVerb = CommentaryMidRace[_random.Next(CommentaryMidRace.Length)];
            Console.ForegroundColor = ConsoleColor.White;
            Console.WriteLine($"\n  {GetFireEmoji()} {results[0].DragonName} {midVerb} {results[1].DragonName}!");
            Console.ResetColor();
        }

        await Task.Delay(1000, ct);

        // Final stretch
        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine("\n  THE FINAL STRETCH!");
        Console.ResetColor();
        await Task.Delay(800, ct);

        // Finish line
        Console.ForegroundColor = ConsoleColor.Green;
        Console.WriteLine($"\n{'=',-60}");
        Console.WriteLine("  RACE RESULTS");
        Console.WriteLine($"{'=',-60}");

        foreach (var result in results)
        {
            var medal = result.Position switch
            {
                1 => "  [1st]",
                2 => "  [2nd]",
                3 => "  [3rd]",
                _ => $"  [{result.Position}th]"
            };
            var color = result.Position switch
            {
                1 => ConsoleColor.Yellow,
                2 => ConsoleColor.Gray,
                3 => ConsoleColor.DarkYellow,
                _ => ConsoleColor.DarkGray
            };

            Console.ForegroundColor = color;
            Console.WriteLine($"{medal} {GetDragonEmoji()} {result.DragonName,-20} Time: {result.FinishTime:F2}s");
        }

        Console.ForegroundColor = ConsoleColor.Yellow;
        Console.WriteLine($"\n  WINNER: {GetDragonEmoji()} {results[0].DragonName} with a time of {results[0].FinishTime:F2}s!");
        Console.WriteLine($"{'=',-60}\n");
        Console.ResetColor();

        // Update race object
        race.Status = "Completed";
        race.Results = results;

        // Write completed race
        QueueHelper.WriteRaceCompleted(race);
        Console.ForegroundColor = ConsoleColor.Cyan;
        Console.WriteLine($"[RaceSimulator] Race #{race.Id} results written to completed queue.");

        // Remove from pending
        QueueHelper.RemoveRacePending(race.Id);
        Console.WriteLine($"[RaceSimulator] Removed race #{race.Id} from pending queue.");

        // Notify Race Schedule Service
        await NotifyRaceScheduleService(race);

        // Write payout trigger
        QueueHelper.WritePayoutPending(race);
        Console.WriteLine($"[RaceSimulator] Payout trigger written for race #{race.Id}.");
        Console.ResetColor();
    }

    private async Task<List<Dragon>> GetDragonStats(List<RaceEntry> entries)
    {
        try
        {
            var client = _httpClientFactory.CreateClient("DragonStable");
            var response = await client.GetAsync("/api/dragons");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                var allDragons = JsonSerializer.Deserialize<List<Dragon>>(json, JsonOptions) ?? new();
                return allDragons.Where(d => entries.Any(e => e.DragonId == d.Id)).ToList();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Dragon Stable API unavailable, using seed data");
        }

        // Fall back to seed data
        var seedDragons = SeedData.GetDragons();
        return seedDragons.Where(d => entries.Any(e => e.DragonId == d.Id)).ToList();
    }

    private async Task NotifyRaceScheduleService(Race race)
    {
        try
        {
            var client = _httpClientFactory.CreateClient("RaceSchedule");
            var content = new StringContent(
                JsonSerializer.Serialize(race, JsonOptions),
                Encoding.UTF8,
                "application/json");
            await client.PostAsync($"/api/races/{race.Id}/complete", content);
            Console.ForegroundColor = ConsoleColor.Cyan;
            Console.WriteLine($"[RaceSimulator] Notified Race Schedule Service of race #{race.Id} completion.");
            Console.ResetColor();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Could not notify Race Schedule Service");
            Console.ForegroundColor = ConsoleColor.DarkYellow;
            Console.WriteLine($"[RaceSimulator] Warning: Could not notify Race Schedule Service ({ex.Message})");
            Console.ResetColor();
        }
    }

    private double RandomRange(double min, double max)
    {
        return min + (_random.NextDouble() * (max - min));
    }

    private static string GetDragonEmoji()
    {
        return "\U0001F409"; // dragon emoji
    }

    private static string GetFireEmoji()
    {
        return "\U0001F525"; // fire emoji
    }
}
