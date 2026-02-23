namespace DragonRacing.Shared.Models;

public class RaceResult
{
    public int DragonId { get; set; }
    public string DragonName { get; set; } = string.Empty;
    public int Position { get; set; }
    public double FinishTime { get; set; }
}
