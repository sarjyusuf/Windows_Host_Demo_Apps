namespace DragonRacing.Shared.Models;

public class RaceEntry
{
    public int DragonId { get; set; }
    public string DragonName { get; set; } = string.Empty;
    public double Odds { get; set; }
    public int Position { get; set; }
    public double FinishTime { get; set; }
}
