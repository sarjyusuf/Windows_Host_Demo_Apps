using Microsoft.AspNetCore.Mvc;
using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text.Json;

namespace DragonRacing.BettingApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class OddsController : ControllerBase
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<OddsController> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public OddsController(IHttpClientFactory httpClientFactory, ILogger<OddsController> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    /// <summary>
    /// GET /api/odds/{raceId} - Get current odds for a race
    /// </summary>
    [HttpGet("{raceId:int}")]
    public async Task<IActionResult> GetOdds(int raceId)
    {
        // Get race info
        Race? race = null;

        try
        {
            var raceClient = _httpClientFactory.CreateClient("RaceSchedule");
            var raceResponse = await raceClient.GetAsync($"/api/races/{raceId}");
            if (raceResponse.IsSuccessStatusCode)
            {
                var json = await raceResponse.Content.ReadAsStringAsync();
                race = JsonSerializer.Deserialize<Race>(json, JsonOptions);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Could not fetch race from Race Schedule Service");
        }

        if (race == null)
            race = SeedData.GetUpcomingRaces().FirstOrDefault(r => r.Id == raceId);

        if (race == null)
            return NotFound($"Race {raceId} not found.");

        // Get dragon stats to compute odds
        var dragons = new List<Dragon>();
        try
        {
            var stableClient = _httpClientFactory.CreateClient("DragonStable");
            var stableResponse = await stableClient.GetAsync("/api/dragons");
            if (stableResponse.IsSuccessStatusCode)
            {
                var json = await stableResponse.Content.ReadAsStringAsync();
                dragons = JsonSerializer.Deserialize<List<Dragon>>(json, JsonOptions) ?? new();
            }
        }
        catch
        {
            dragons = SeedData.GetDragons();
        }

        if (dragons.Count == 0)
            dragons = SeedData.GetDragons();

        // Calculate odds for each entry based on dragon stats
        var oddsResult = new List<object>();
        foreach (var entry in race.Entries)
        {
            var dragon = dragons.FirstOrDefault(d => d.Id == entry.DragonId);
            double odds;
            if (dragon != null)
            {
                // Higher rated = lower odds (favorites)
                var rating = dragon.OverallRating;
                odds = Math.Round(10.0 - (rating / 15.0), 1);
                if (odds < 1.5) odds = 1.5;
            }
            else
            {
                odds = entry.Odds > 0 ? entry.Odds : 4.0;
            }

            oddsResult.Add(new
            {
                entry.DragonId,
                entry.DragonName,
                Odds = odds,
                DragonRating = dragon?.OverallRating ?? 0,
                DragonWins = dragon?.Wins ?? 0,
                DragonLosses = dragon?.Losses ?? 0
            });
        }

        return Ok(new
        {
            RaceId = raceId,
            race.TrackName,
            race.Status,
            race.StartTime,
            Entries = oddsResult
        });
    }
}
