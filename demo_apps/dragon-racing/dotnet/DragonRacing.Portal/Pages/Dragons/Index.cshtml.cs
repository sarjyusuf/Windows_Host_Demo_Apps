using Microsoft.AspNetCore.Mvc.RazorPages;
using DragonRacing.Shared;
using DragonRacing.Shared.Models;
using System.Text.Json;

namespace DragonRacing.Portal.Pages.Dragons;

public class IndexModel : PageModel
{
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<IndexModel> _logger;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public List<Dragon> Dragons { get; set; } = new();

    public IndexModel(IHttpClientFactory httpClientFactory, ILogger<IndexModel> logger)
    {
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    public async Task OnGetAsync()
    {
        try
        {
            var client = _httpClientFactory.CreateClient("DragonStable");
            var response = await client.GetAsync("/api/dragons");
            if (response.IsSuccessStatusCode)
            {
                var json = await response.Content.ReadAsStringAsync();
                Dragons = JsonSerializer.Deserialize<List<Dragon>>(json, JsonOptions) ?? new();
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Dragon Stable API unavailable, using seed data");
        }

        if (Dragons.Count == 0)
            Dragons = SeedData.GetDragons();
    }
}
