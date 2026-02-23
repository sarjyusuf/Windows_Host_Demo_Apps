using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.RazorPages;
using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text;
using System.Text.Json;

namespace DragonRacing.Portal.Pages.Bet;

public class IndexModel : PageModel
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<IndexModel> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNameCaseInsensitive = true,
        WriteIndented = true
    };

    public List<Race> AvailableRaces { get; set; } = new();
    public List<Dragon> AvailableDragons { get; set; } = new();
    public List<DragonRacing.Shared.Models.Bet> RecentBets { get; set; } = new();

    [BindProperty] public string BettorName { get; set; } = string.Empty;
    [BindProperty] public int SelectedRaceId { get; set; }
    [BindProperty] public int DragonId { get; set; }
    [BindProperty] public double Amount { get; set; }

    public string? SuccessMessage { get; set; }
    public string? ErrorMessage { get; set; }

    public IndexModel(IHttpClientFactory httpClientFactory, ILogger<IndexModel> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    public async Task OnGetAsync()
    {
        await LoadDataAsync();
    }

    public async Task<IActionResult> OnPostAsync()
    {
        await LoadDataAsync();

        if (string.IsNullOrWhiteSpace(BettorName) || SelectedRaceId == 0 || DragonId == 0 || Amount <= 0)
        {
            ErrorMessage = "Please fill in all fields correctly.";
            return Page();
        }

        try
        {
            var client = _httpClientFactory.CreateClient("BettingApi");
            var betRequest = new
            {
                BettorName,
                RaceId = SelectedRaceId,
                DragonId,
                Amount
            };

            var content = new StringContent(
                JsonSerializer.Serialize(betRequest, JsonOptions),
                Encoding.UTF8,
                "application/json");

            var response = await client.PostAsync("/api/bets", content);

            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                var placedBet = JsonSerializer.Deserialize<DragonRacing.Shared.Models.Bet>(json, JsonOptions);
                SuccessMessage = $"Bet placed successfully! Bet #{placedBet?.Id} - {Amount:N0} gold on Dragon #{DragonId} at {placedBet?.Odds:F1}x odds. Potential payout: {(Amount * (placedBet?.Odds ?? 1)):N0} gold!";

                // Reload bets
                await LoadBetsAsync();
            }
            else
            {
                var errorBody = await response.Content.ReadAsStringAsync();
                ErrorMessage = $"Failed to place bet: {errorBody}";
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error placing bet via Betting API");
            ErrorMessage = $"Betting API is unavailable. Please try again later. ({ex.Message})";
        }

        return Page();
    }

    private async Task LoadDataAsync()
    {
        // Load races
        try
        {
            var client = _httpClientFactory.CreateClient("RaceSchedule");
            var response = await client.GetAsync("/api/races");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                var allRaces = JsonSerializer.Deserialize<List<Race>>(json, JsonOptions) ?? new();
                AvailableRaces = allRaces.Where(r => r.Status == "Scheduled").ToList();
            }
        }
        catch
        {
            AvailableRaces = SeedData.GetUpcomingRaces();
        }

        if (AvailableRaces.Count == 0)
            AvailableRaces = SeedData.GetUpcomingRaces();

        // Load dragons
        try
        {
            var client = _httpClientFactory.CreateClient("DragonStable");
            var response = await client.GetAsync("/api/dragons");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                AvailableDragons = JsonSerializer.Deserialize<List<Dragon>>(json, JsonOptions) ?? new();
            }
        }
        catch
        {
            AvailableDragons = SeedData.GetDragons();
        }

        if (AvailableDragons.Count == 0)
            AvailableDragons = SeedData.GetDragons();

        // Load recent bets
        await LoadBetsAsync();
    }

    private async Task LoadBetsAsync()
    {
        try
        {
            var client = _httpClientFactory.CreateClient("BettingApi");
            var response = await client.GetAsync("/api/bets");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                RecentBets = JsonSerializer.Deserialize<List<DragonRacing.Shared.Models.Bet>>(json, JsonOptions) ?? new();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Could not load recent bets from Betting API");
        }
    }
}
