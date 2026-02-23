using Microsoft.AspNetCore.Mvc;
using DragonRacing.BettingApi.Data;
using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text.Json;

namespace DragonRacing.BettingApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class BetsController : ControllerBase
{
    private readonly BetDatabase _db;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<BetsController> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public BetsController(BetDatabase db, IHttpClientFactory httpClientFactory, ILogger<BetsController> logger)
    {
        _db = db;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    /// <summary>
    /// GET /api/bets - List all bets
    /// </summary>
    [HttpGet]
    public IActionResult GetAllBets()
    {
        var bets = _db.GetAllBets();
        return Ok(bets);
    }

    /// <summary>
    /// POST /api/bets - Place a new bet
    /// </summary>
    [HttpPost]
    public async Task<IActionResult> PlaceBet([FromBody] PlaceBetRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.BettorName))
            return BadRequest("BettorName is required.");

        if (request.Amount <= 0)
            return BadRequest("Amount must be greater than 0.");

        if (request.RaceId <= 0)
            return BadRequest("RaceId is required.");

        if (request.DragonId <= 0)
            return BadRequest("DragonId is required.");

        // Try to validate race exists
        Race? race = null;
        try
        {
            var client = _httpClientFactory.CreateClient("RaceSchedule");
            var response = await client.GetAsync($"/api/races/{request.RaceId}");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                race = JsonSerializer.Deserialize<Race>(json, JsonOptions);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Race Schedule Service unavailable, falling back to seed data for validation");
        }

        // Fall back to seed data
        if (race == null)
        {
            race = SeedData.GetUpcomingRaces().FirstOrDefault(r => r.Id == request.RaceId);
        }

        if (race == null)
            return NotFound($"Race {request.RaceId} not found.");

        if (race.Status == "Completed")
            return BadRequest("Cannot bet on a completed race.");

        // Calculate odds based on dragon stats
        double odds = await CalculateOddsForDragon(request.DragonId, race);

        var bet = new Bet
        {
            RaceId = request.RaceId,
            DragonId = request.DragonId,
            BettorName = request.BettorName,
            Amount = request.Amount,
            Odds = odds,
            Status = "Pending",
            Payout = 0
        };

        var insertedBet = _db.InsertBet(bet);

        _logger.LogInformation(
            "Bet placed: #{Id} - {Bettor} wagered {Amount} gold on Dragon #{DragonId} in Race #{RaceId} at {Odds}x odds",
            insertedBet.Id, request.BettorName, request.Amount, request.DragonId, request.RaceId, odds);

        return Ok(insertedBet);
    }

    /// <summary>
    /// GET /api/bets/{id} - Get bet by ID
    /// </summary>
    [HttpGet("{id:int}")]
    public IActionResult GetBetById(int id)
    {
        var bet = _db.GetBetById(id);
        if (bet == null)
            return NotFound($"Bet #{id} not found.");

        return Ok(bet);
    }

    /// <summary>
    /// GET /api/bets/race/{raceId} - Get all bets for a race
    /// </summary>
    [HttpGet("race/{raceId:int}")]
    public IActionResult GetBetsByRace(int raceId)
    {
        var bets = _db.GetBetsByRaceId(raceId);
        return Ok(bets);
    }

    /// <summary>
    /// PUT /api/bets/{id}/status - Update bet status (used by payout processor)
    /// </summary>
    [HttpPut("{id:int}/status")]
    public IActionResult UpdateBetStatus(int id, [FromBody] UpdateBetStatusRequest request)
    {
        var bet = _db.GetBetById(id);
        if (bet == null)
            return NotFound($"Bet #{id} not found.");

        _db.UpdateBetStatus(id, request.Status, request.Payout);
        _logger.LogInformation("Bet #{Id} status updated to {Status}, payout: {Payout}", id, request.Status, request.Payout);

        return Ok(new { Id = id, Status = request.Status, Payout = request.Payout });
    }

    private async Task<double> CalculateOddsForDragon(int dragonId, Race race)
    {
        // Check if dragon has odds set in race entries
        var entry = race.Entries.FirstOrDefault(e => e.DragonId == dragonId);
        if (entry != null && entry.Odds > 0)
            return entry.Odds;

        // Try to get dragon stats from Stable API and calculate
        try
        {
            var client = _httpClientFactory.CreateClient("DragonStable");
            var response = await client.GetAsync($"/api/dragons/{dragonId}");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                var dragon = JsonSerializer.Deserialize<Dragon>(json, JsonOptions);
                if (dragon != null)
                {
                    // Higher rated dragons get lower odds (more likely to win)
                    var rating = dragon.OverallRating;
                    return Math.Round(10.0 - (rating / 15.0), 1);
                }
            }
        }
        catch { /* fall through */ }

        // Fall back to seed data
        var seedDragon = SeedData.GetDragons().FirstOrDefault(d => d.Id == dragonId);
        if (seedDragon != null)
        {
            var rating = seedDragon.OverallRating;
            return Math.Round(10.0 - (rating / 15.0), 1);
        }

        // Default odds
        return 4.0;
    }
}

public class PlaceBetRequest
{
    public string BettorName { get; set; } = string.Empty;
    public int RaceId { get; set; }
    public int DragonId { get; set; }
    public double Amount { get; set; }
}

public class UpdateBetStatusRequest
{
    public string Status { get; set; } = string.Empty;
    public double Payout { get; set; }
}
