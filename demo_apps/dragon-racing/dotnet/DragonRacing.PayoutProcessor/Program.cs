using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text;
using System.Text.Json;

BannerHelper.PrintPayoutProcessorBanner();

var jsonOptions = new JsonSerializerOptions { PropertyNameCaseInsensitive = true, WriteIndented = true };

// Configure HttpClient
var httpClient = new HttpClient
{
    BaseAddress = new Uri("http://localhost:5001"),
    Timeout = TimeSpan.FromSeconds(10)
};

// Ensure queue directories exist
QueueHelper.EnsureDirectoriesExist();

Console.ForegroundColor = ConsoleColor.Magenta;
Console.WriteLine("[PayoutProcessor] Watching for payout files...");
Console.WriteLine($"  Payouts Pending:   {QueueHelper.PayoutsPendingDir}");
Console.WriteLine($"  Payouts Completed: {QueueHelper.PayoutsCompletedDir}");
Console.ResetColor();
Console.WriteLine();

// Setup cancellation
var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    cts.Cancel();
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine("\n[PayoutProcessor] Shutdown signal received. Stopping gracefully...");
    Console.ResetColor();
};

// Main polling loop
while (!cts.Token.IsCancellationRequested)
{
    try
    {
        var pendingFiles = QueueHelper.GetPendingPayoutFiles();

        foreach (var file in pendingFiles)
        {
            if (cts.Token.IsCancellationRequested) break;

            Console.ForegroundColor = ConsoleColor.Magenta;
            Console.WriteLine($"\n[PayoutProcessor] Found payout file: {Path.GetFileName(file)}");
            Console.ResetColor();

            var race = QueueHelper.ReadJsonFile<Race>(file);
            if (race == null)
            {
                Console.ForegroundColor = ConsoleColor.Red;
                Console.WriteLine($"[PayoutProcessor] Could not read payout file: {file}");
                Console.ResetColor();
                continue;
            }

            await ProcessPayouts(race);
        }
    }
    catch (Exception ex)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine($"[PayoutProcessor] Error: {ex.Message}");
        Console.ResetColor();
    }

    try
    {
        await Task.Delay(5000, cts.Token);
    }
    catch (OperationCanceledException)
    {
        break;
    }
}

Console.ForegroundColor = ConsoleColor.Magenta;
Console.WriteLine("[PayoutProcessor] Stopped.");
Console.ResetColor();

async Task ProcessPayouts(Race race)
{
    Console.ForegroundColor = ConsoleColor.Magenta;
    Console.WriteLine($"\n{'=',-60}");
    Console.WriteLine($"  PROCESSING PAYOUTS - Race #{race.Id}: {race.TrackName}");
    Console.WriteLine($"{'=',-60}");
    Console.ResetColor();

    // Get the winner
    var winner = race.Results.OrderBy(r => r.Position).FirstOrDefault();
    if (winner == null)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine("[PayoutProcessor] No results found for this race. Skipping.");
        Console.ResetColor();
        return;
    }

    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine($"  Winner: \U0001F409 {winner.DragonName} (Position #{winner.Position}, Time: {winner.FinishTime:F2}s)");
    Console.ResetColor();

    // Get all bets for this race from Betting API
    List<Bet> bets;
    try
    {
        var response = await httpClient.GetAsync($"/api/bets/race/{race.Id}");
        if (!response.IsSuccessStatusCode)
        {
            Console.ForegroundColor = ConsoleColor.DarkYellow;
            Console.WriteLine($"[PayoutProcessor] No bets found for race #{race.Id} (HTTP {response.StatusCode})");
            Console.ResetColor();
            QueueHelper.MovePayoutToCompleted(race.Id);
            return;
        }

        var json = await response.Content.ReadAsStringAsync();
        bets = JsonSerializer.Deserialize<List<Bet>>(json, jsonOptions) ?? new();
    }
    catch (Exception ex)
    {
        Console.ForegroundColor = ConsoleColor.Red;
        Console.WriteLine($"[PayoutProcessor] Error fetching bets from Betting API: {ex.Message}");
        Console.ResetColor();
        return; // Don't move to completed - retry next cycle
    }

    if (bets.Count == 0)
    {
        Console.ForegroundColor = ConsoleColor.DarkYellow;
        Console.WriteLine($"  No bets found for race #{race.Id}. Nothing to pay out.");
        Console.ResetColor();
        QueueHelper.MovePayoutToCompleted(race.Id);
        return;
    }

    Console.ForegroundColor = ConsoleColor.White;
    Console.WriteLine($"  Found {bets.Count} bet(s) to process.\n");
    Console.ResetColor();

    int winningBets = 0;
    int losingBets = 0;
    double totalPayouts = 0;

    foreach (var bet in bets.Where(b => b.Status == "Pending"))
    {
        bool isWinner = bet.DragonId == winner.DragonId;
        string newStatus;
        double payout;

        if (isWinner)
        {
            newStatus = "Won";
            payout = Math.Round(bet.Amount * bet.Odds, 2);
            totalPayouts += payout;
            winningBets++;

            Console.ForegroundColor = ConsoleColor.Green;
            Console.WriteLine($"  \U0001F4B0 {bet.BettorName} WINS {payout:N0} gold!");
            Console.WriteLine($"     Bet: {bet.Amount:N0} gold on \U0001F409 {winner.DragonName} at {bet.Odds:F1}x odds");
            Console.ResetColor();
        }
        else
        {
            newStatus = "Lost";
            payout = 0;
            losingBets++;

            Console.ForegroundColor = ConsoleColor.DarkRed;
            Console.WriteLine($"  \U0001F4A8 {bet.BettorName} lost {bet.Amount:N0} gold (bet on Dragon #{bet.DragonId})");
            Console.ResetColor();
        }

        // Update bet status via Betting API
        try
        {
            var updateRequest = new { Status = newStatus, Payout = payout };
            var content = new StringContent(
                JsonSerializer.Serialize(updateRequest, jsonOptions),
                Encoding.UTF8,
                "application/json");

            await httpClient.PutAsync($"/api/bets/{bet.Id}/status", content);
        }
        catch (Exception ex)
        {
            Console.ForegroundColor = ConsoleColor.Red;
            Console.WriteLine($"  [PayoutProcessor] Error updating bet #{bet.Id}: {ex.Message}");
            Console.ResetColor();
        }
    }

    // Summary
    Console.ForegroundColor = ConsoleColor.Magenta;
    Console.WriteLine($"\n  {'=',-50}");
    Console.WriteLine($"  PAYOUT SUMMARY - Race #{race.Id}");
    Console.WriteLine($"  {'=',-50}");
    Console.ForegroundColor = ConsoleColor.Green;
    Console.WriteLine($"  \U0001F3C6 Winning Bets: {winningBets}");
    Console.ForegroundColor = ConsoleColor.Red;
    Console.WriteLine($"  \U0001F4A8 Losing Bets:  {losingBets}");
    Console.ForegroundColor = ConsoleColor.Yellow;
    Console.WriteLine($"  \U0001F4B0 Total Payouts: {totalPayouts:N0} gold");
    Console.ForegroundColor = ConsoleColor.Magenta;
    Console.WriteLine($"  {'=',-50}\n");
    Console.ResetColor();

    // Move payout file to completed
    QueueHelper.MovePayoutToCompleted(race.Id);
    Console.ForegroundColor = ConsoleColor.Magenta;
    Console.WriteLine($"[PayoutProcessor] Payout for race #{race.Id} completed and moved to completed queue.");
    Console.ResetColor();
}
