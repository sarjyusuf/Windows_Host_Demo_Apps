using Microsoft.Data.Sqlite;
using DragonRacing.Shared.Models;

namespace DragonRacing.BettingApi.Data;

public class BetDatabase
{
    private readonly string _connectionString;
    private readonly ILogger<BetDatabase> _logger;

    public BetDatabase(IConfiguration configuration, ILogger<BetDatabase> logger)
    {
        var dbPath = configuration["Database:Path"] ?? Path.Combine("C:", "dragon-racing", "data", "bets.db");
        var dbDir = Path.GetDirectoryName(dbPath);
        if (!string.IsNullOrEmpty(dbDir))
            Directory.CreateDirectory(dbDir);

        _connectionString = $"Data Source={dbPath}";
        _logger = logger;
    }

    public void Initialize()
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = @"
            CREATE TABLE IF NOT EXISTS Bets (
                Id INTEGER PRIMARY KEY AUTOINCREMENT,
                RaceId INTEGER NOT NULL,
                DragonId INTEGER NOT NULL,
                BettorName TEXT NOT NULL,
                Amount REAL NOT NULL,
                Odds REAL NOT NULL DEFAULT 1.0,
                Status TEXT NOT NULL DEFAULT 'Pending',
                Payout REAL NOT NULL DEFAULT 0.0,
                CreatedAt TEXT NOT NULL DEFAULT (datetime('now'))
            );
        ";
        command.ExecuteNonQuery();
        _logger.LogInformation("Database initialized at {ConnectionString}", _connectionString);
    }

    public List<Bet> GetAllBets()
    {
        var bets = new List<Bet>();
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = "SELECT Id, RaceId, DragonId, BettorName, Amount, Odds, Status, Payout FROM Bets ORDER BY Id DESC";

        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            bets.Add(ReadBet(reader));
        }
        return bets;
    }

    public Bet? GetBetById(int id)
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = "SELECT Id, RaceId, DragonId, BettorName, Amount, Odds, Status, Payout FROM Bets WHERE Id = @id";
        command.Parameters.AddWithValue("@id", id);

        using var reader = command.ExecuteReader();
        if (reader.Read())
            return ReadBet(reader);

        return null;
    }

    public List<Bet> GetBetsByRaceId(int raceId)
    {
        var bets = new List<Bet>();
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = "SELECT Id, RaceId, DragonId, BettorName, Amount, Odds, Status, Payout FROM Bets WHERE RaceId = @raceId ORDER BY Id DESC";
        command.Parameters.AddWithValue("@raceId", raceId);

        using var reader = command.ExecuteReader();
        while (reader.Read())
        {
            bets.Add(ReadBet(reader));
        }
        return bets;
    }

    public Bet InsertBet(Bet bet)
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = @"
            INSERT INTO Bets (RaceId, DragonId, BettorName, Amount, Odds, Status, Payout)
            VALUES (@raceId, @dragonId, @bettorName, @amount, @odds, @status, @payout);
            SELECT last_insert_rowid();
        ";
        command.Parameters.AddWithValue("@raceId", bet.RaceId);
        command.Parameters.AddWithValue("@dragonId", bet.DragonId);
        command.Parameters.AddWithValue("@bettorName", bet.BettorName);
        command.Parameters.AddWithValue("@amount", bet.Amount);
        command.Parameters.AddWithValue("@odds", bet.Odds);
        command.Parameters.AddWithValue("@status", bet.Status);
        command.Parameters.AddWithValue("@payout", bet.Payout);

        bet.Id = Convert.ToInt32(command.ExecuteScalar());
        return bet;
    }

    public void UpdateBetStatus(int id, string status, double payout)
    {
        using var connection = new SqliteConnection(_connectionString);
        connection.Open();

        var command = connection.CreateCommand();
        command.CommandText = "UPDATE Bets SET Status = @status, Payout = @payout WHERE Id = @id";
        command.Parameters.AddWithValue("@id", id);
        command.Parameters.AddWithValue("@status", status);
        command.Parameters.AddWithValue("@payout", payout);

        command.ExecuteNonQuery();
    }

    private static Bet ReadBet(SqliteDataReader reader) => new()
    {
        Id = reader.GetInt32(0),
        RaceId = reader.GetInt32(1),
        DragonId = reader.GetInt32(2),
        BettorName = reader.GetString(3),
        Amount = reader.GetDouble(4),
        Odds = reader.GetDouble(5),
        Status = reader.GetString(6),
        Payout = reader.GetDouble(7)
    };
}
