using DragonRacing.Shared.Models;

namespace DragonRacing.Shared;

public static class SeedData
{
    public static List<Dragon> GetDragons() => new()
    {
        new Dragon { Id = 1, Name = "Shadowfang", Breed = "Shadow Wyrm", Speed = 92, Stamina = 78, Agility = 88, Wins = 15, Losses = 5, OwnerName = "Lord Darkmoor", Level = 12 },
        new Dragon { Id = 2, Name = "Emberclaw", Breed = "Fire Drake", Speed = 85, Stamina = 90, Agility = 75, Wins = 18, Losses = 7, OwnerName = "Lady Pyra", Level = 14 },
        new Dragon { Id = 3, Name = "Frostbite", Breed = "Ice Serpent", Speed = 80, Stamina = 85, Agility = 92, Wins = 12, Losses = 8, OwnerName = "Baron Frost", Level = 10 },
        new Dragon { Id = 4, Name = "Thunderwing", Breed = "Storm Dragon", Speed = 95, Stamina = 70, Agility = 82, Wins = 20, Losses = 4, OwnerName = "Queen Tempest", Level = 16 },
        new Dragon { Id = 5, Name = "Nightshade", Breed = "Void Stalker", Speed = 88, Stamina = 82, Agility = 90, Wins = 14, Losses = 6, OwnerName = "Shadow Mistress Nyx", Level = 13 },
        new Dragon { Id = 6, Name = "Blazeheart", Breed = "Inferno Titan", Speed = 78, Stamina = 95, Agility = 72, Wins = 16, Losses = 9, OwnerName = "Forge King Vulcan", Level = 15 },
        new Dragon { Id = 7, Name = "Stormscale", Breed = "Thunder Serpent", Speed = 90, Stamina = 76, Agility = 86, Wins = 11, Losses = 9, OwnerName = "Captain Bolt", Level = 11 },
        new Dragon { Id = 8, Name = "Venomspire", Breed = "Plague Wyvern", Speed = 82, Stamina = 88, Agility = 80, Wins = 13, Losses = 7, OwnerName = "Witch Doctor Malakai", Level = 12 }
    };

    public static List<Race> GetUpcomingRaces() => new()
    {
        new Race
        {
            Id = 101,
            TrackName = "Dragon's Peak Circuit",
            Status = "Scheduled",
            StartTime = DateTime.UtcNow.AddHours(2),
            Entries = new List<RaceEntry>
            {
                new() { DragonId = 1, DragonName = "Shadowfang", Odds = 3.5 },
                new() { DragonId = 4, DragonName = "Thunderwing", Odds = 2.8 },
                new() { DragonId = 5, DragonName = "Nightshade", Odds = 4.0 },
                new() { DragonId = 7, DragonName = "Stormscale", Odds = 5.2 }
            }
        },
        new Race
        {
            Id = 102,
            TrackName = "Inferno Valley Sprint",
            Status = "Scheduled",
            StartTime = DateTime.UtcNow.AddHours(5),
            Entries = new List<RaceEntry>
            {
                new() { DragonId = 2, DragonName = "Emberclaw", Odds = 3.0 },
                new() { DragonId = 3, DragonName = "Frostbite", Odds = 4.5 },
                new() { DragonId = 6, DragonName = "Blazeheart", Odds = 3.8 },
                new() { DragonId = 8, DragonName = "Venomspire", Odds = 4.2 }
            }
        },
        new Race
        {
            Id = 103,
            TrackName = "Frostfang Mountain Rally",
            Status = "Scheduled",
            StartTime = DateTime.UtcNow.AddHours(8),
            Entries = new List<RaceEntry>
            {
                new() { DragonId = 1, DragonName = "Shadowfang", Odds = 3.2 },
                new() { DragonId = 2, DragonName = "Emberclaw", Odds = 3.5 },
                new() { DragonId = 3, DragonName = "Frostbite", Odds = 2.8 },
                new() { DragonId = 4, DragonName = "Thunderwing", Odds = 3.0 },
                new() { DragonId = 5, DragonName = "Nightshade", Odds = 4.0 },
                new() { DragonId = 6, DragonName = "Blazeheart", Odds = 4.5 }
            }
        }
    };
}
