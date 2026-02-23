namespace DragonRacing.Shared.Models;

public class Dragon
{
    public int Id { get; set; }
    public string Name { get; set; } = string.Empty;
    public string Breed { get; set; } = string.Empty;
    public double Speed { get; set; }
    public double Stamina { get; set; }
    public double Agility { get; set; }
    public int Wins { get; set; }
    public int Losses { get; set; }
    public string OwnerName { get; set; } = string.Empty;
    public int Level { get; set; } = 1;

    public double OverallRating => (Speed + Stamina + Agility) / 3.0;
}
