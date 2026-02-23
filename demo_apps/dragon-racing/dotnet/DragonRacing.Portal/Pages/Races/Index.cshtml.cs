using Microsoft.AspNetCore.Mvc.RazorPages;
using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text.Json;

namespace DragonRacing.Portal.Pages.Races;

public class IndexModel : PageModel
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<IndexModel> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public List<Race> Races { get; set; } = new();

    public IndexModel(IHttpClientFactory httpClientFactory, ILogger<IndexModel> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    public async Task OnGetAsync()
    {
        try
        {
            var client = _httpClientFactory.CreateClient("RaceSchedule");
            var response = await client.GetAsync("/api/races");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                Races = JsonSerializer.Deserialize<List<Race>>(json, JsonOptions) ?? new();
                return;
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Race Schedule Service unavailable, using seed data + completed queue");
        }

        // Fall back to seed data for upcoming + completed from queue
        Races = SeedData.GetUpcomingRaces();
        Races.AddRange(QueueHelper.GetCompletedRaces());
    }
}
