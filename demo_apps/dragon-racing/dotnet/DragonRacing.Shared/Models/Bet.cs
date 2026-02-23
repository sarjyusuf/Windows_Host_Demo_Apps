namespace DragonRacing.Shared.Models;

public class Bet
{
    public int Id { get; set; }
    public int RaceId { get; set; }
    public int DragonId { get; set; }
    public string BettorName { get; set; } = string.Empty;
    public double Amount { get; set; }
    public double Odds { get; set; }
    public string Status { get; set; } = "Pending"; // Pending, Won, Lost, PaidOut
    public double Payout { get; set; }
}
