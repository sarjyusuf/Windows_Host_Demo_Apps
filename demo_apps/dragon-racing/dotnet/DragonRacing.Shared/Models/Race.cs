namespace DragonRacing.Shared.Models;

public class Race
{
    public int Id { get; set; }
    public string TrackName { get; set; } = string.Empty;
    public string Status { get; set; } = "Scheduled"; // Scheduled, InProgress, Completed
    public DateTime StartTime { get; set; }
    public List<RaceEntry> Entries { get; set; } = new();
    public List<RaceResult> Results { get; set; } = new();
}
