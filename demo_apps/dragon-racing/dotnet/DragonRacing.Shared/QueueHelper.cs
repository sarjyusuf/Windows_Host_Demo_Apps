using System.Text.Json;
using DragonRacing.Shared.Models;

namespace DragonRacing.Shared;

public static class QueueHelper
{
    private static readonly string BaseDir = Path.Combine("C:", "dragon-racing", "queues");

    public static readonly string RacesPendingDir = Path.Combine(BaseDir, "races", "pending");
    public static readonly string RacesCompletedDir = Path.Combine(BaseDir, "races", "completed");
    public static readonly string PayoutsPendingDir = Path.Combine(BaseDir, "payouts", "pending");
    public static readonly string PayoutsCompletedDir = Path.Combine(BaseDir, "payouts", "completed");

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    static QueueHelper()
    {
        EnsureDirectoriesExist();
    }

    public static void EnsureDirectoriesExist()
    {
        Directory.CreateDirectory(RacesPendingDir);
        Directory.CreateDirectory(RacesCompletedDir);
        Directory.CreateDirectory(PayoutsPendingDir);
        Directory.CreateDirectory(PayoutsCompletedDir);
    }

    public static void WriteRacePending(Race race)
    {
        var filePath = Path.Combine(RacesPendingDir, $"{race.Id}.json");
        var json = JsonSerializer.Serialize(race, JsonOptions);
        File.WriteAllText(filePath, json);
    }

    public static void WriteRaceCompleted(Race race)
    {
        var filePath = Path.Combine(RacesCompletedDir, $"{race.Id}.json");
        var json = JsonSerializer.Serialize(race, JsonOptions);
        File.WriteAllText(filePath, json);
    }

    public static void WritePayoutPending(Race race)
    {
        var filePath = Path.Combine(PayoutsPendingDir, $"{race.Id}.json");
        var json = JsonSerializer.Serialize(race, JsonOptions);
        File.WriteAllText(filePath, json);
    }

    public static void MovePayoutToCompleted(int raceId)
    {
        var source = Path.Combine(PayoutsPendingDir, $"{raceId}.json");
        var dest = Path.Combine(PayoutsCompletedDir, $"{raceId}.json");
        if (File.Exists(source))
        {
            if (File.Exists(dest)) File.Delete(dest);
            File.Move(source, dest);
        }
    }

    public static void RemoveRacePending(int raceId)
    {
        var filePath = Path.Combine(RacesPendingDir, $"{raceId}.json");
        if (File.Exists(filePath))
            File.Delete(filePath);
    }

    public static T? ReadJsonFile<T>(string filePath)
    {
        if (!File.Exists(filePath)) return default;
        var json = File.ReadAllText(filePath);
        return JsonSerializer.Deserialize<T>(json, JsonOptions);
    }

    public static string[] GetPendingRaceFiles()
    {
        EnsureDirectoriesExist();
        return Directory.GetFiles(RacesPendingDir, "*.json");
    }

    public static string[] GetPendingPayoutFiles()
    {
        EnsureDirectoriesExist();
        return Directory.GetFiles(PayoutsPendingDir, "*.json");
    }

    public static List<Race> GetCompletedRaces()
    {
        EnsureDirectoriesExist();
        var races = new List<Race>();
        foreach (var file in Directory.GetFiles(RacesCompletedDir, "*.json"))
        {
            var race = ReadJsonFile<Race>(file);
            if (race != null) races.Add(race);
        }
        return races;
    }
}
