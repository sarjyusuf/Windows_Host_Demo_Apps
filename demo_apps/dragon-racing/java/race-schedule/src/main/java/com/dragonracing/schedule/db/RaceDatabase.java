package com.dragonracing.schedule.db;

import com.dragonracing.schedule.model.Race;
import com.dragonracing.schedule.model.RaceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RaceDatabase {

    private static final Logger log = LoggerFactory.getLogger(RaceDatabase.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String dbPath;

    public RaceDatabase(String dbPath) {
        this.dbPath = dbPath;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public void initialize() {
        // Ensure data directory exists
        File dataDir = new File(dbPath).getParentFile();
        if (dataDir != null && !dataDir.exists()) {
            dataDir.mkdirs();
            log.info("Created data directory: {}", dataDir.getAbsolutePath());
        }

        createTables();
        seedRaces();
    }

    private void createTables() {
        log.info("Creating race tables if not exists...");
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS races (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    track_name TEXT NOT NULL,
                    scheduled_time TEXT,
                    status TEXT DEFAULT 'Scheduled',
                    results TEXT,
                    created_at TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS race_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    race_id INTEGER NOT NULL,
                    dragon_id INTEGER NOT NULL,
                    dragon_name TEXT,
                    entered_at TEXT,
                    FOREIGN KEY (race_id) REFERENCES races(id)
                )
            """);
        } catch (SQLException e) {
            log.error("Failed to create tables", e);
            throw new RuntimeException(e);
        }
    }

    private void seedRaces() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM races")) {
            if (rs.next() && rs.getInt(1) > 0) {
                log.info("Races already seeded ({} races found).", rs.getInt(1));
                return;
            }
        } catch (SQLException e) {
            log.error("Failed to check race count", e);
            return;
        }

        log.info("Seeding upcoming races...");
        LocalDateTime now = LocalDateTime.now();

        insertRace("Dragon's Peak Circuit", now.plusHours(2));
        insertRace("Inferno Valley Sprint", now.plusHours(6));
        insertRace("Frostfang Mountain Rally", now.plusDays(1));

        log.info("3 races have been scheduled! The crowd roars in anticipation!");
    }

    private void insertRace(String trackName, LocalDateTime scheduledTime) {
        String sql = "INSERT INTO races (track_name, scheduled_time, status, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, trackName);
            ps.setString(2, scheduledTime.format(DT_FMT));
            ps.setString(3, Race.Status.Scheduled.name());
            ps.setString(4, LocalDateTime.now().format(DT_FMT));
            ps.executeUpdate();
            log.info("  Scheduled race at: {} ({})", trackName, scheduledTime.format(DT_FMT));
        } catch (SQLException e) {
            log.error("Failed to insert race", e);
        }
    }

    public List<Race> findAllRaces() {
        List<Race> races = new ArrayList<>();
        String sql = "SELECT * FROM races ORDER BY scheduled_time";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Race race = mapRace(rs);
                race.setEntries(findEntriesByRaceId(race.getId()));
                races.add(race);
            }
        } catch (SQLException e) {
            log.error("Failed to query races", e);
        }
        return races;
    }

    public Optional<Race> findRaceById(long id) {
        String sql = "SELECT * FROM races WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Race race = mapRace(rs);
                    race.setEntries(findEntriesByRaceId(race.getId()));
                    return Optional.of(race);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find race by id", e);
        }
        return Optional.empty();
    }

    public Race createRace(String trackName, LocalDateTime scheduledTime) {
        String sql = "INSERT INTO races (track_name, scheduled_time, status, created_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String now = LocalDateTime.now().format(DT_FMT);
            ps.setString(1, trackName);
            ps.setString(2, scheduledTime != null ? scheduledTime.format(DT_FMT) : now);
            ps.setString(3, Race.Status.Scheduled.name());
            ps.setString(4, now);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findRaceById(keys.getLong(1)).orElse(null);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to create race", e);
        }
        return null;
    }

    public RaceEntry addEntry(long raceId, long dragonId, String dragonName) {
        String sql = "INSERT INTO race_entries (race_id, dragon_id, dragon_name, entered_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, raceId);
            ps.setLong(2, dragonId);
            ps.setString(3, dragonName);
            ps.setString(4, LocalDateTime.now().format(DT_FMT));
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    RaceEntry entry = new RaceEntry(raceId, dragonId, dragonName);
                    entry.setId(keys.getLong(1));
                    return entry;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to add entry", e);
        }
        return null;
    }

    public void updateRaceStatus(long raceId, Race.Status status) {
        String sql = "UPDATE races SET status = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, raceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update race status", e);
        }
    }

    public void updateRaceResults(long raceId, String resultsJson) {
        String sql = "UPDATE races SET status = ?, results = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Race.Status.Completed.name());
            ps.setString(2, resultsJson);
            ps.setLong(3, raceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to update race results", e);
        }
    }

    public List<Race> findStaleRaces() {
        String sql = "SELECT * FROM races WHERE status = 'InProgress'";
        List<Race> stale = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stale.add(mapRace(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find stale races", e);
        }
        return stale;
    }

    public long raceCount() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM races")) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException e) {
            log.error("Failed to count races", e);
        }
        return 0;
    }

    private List<RaceEntry> findEntriesByRaceId(long raceId) {
        List<RaceEntry> entries = new ArrayList<>();
        String sql = "SELECT * FROM race_entries WHERE race_id = ? ORDER BY entered_at";
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, raceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RaceEntry entry = new RaceEntry();
                    entry.setId(rs.getLong("id"));
                    entry.setRaceId(rs.getLong("race_id"));
                    entry.setDragonId(rs.getLong("dragon_id"));
                    entry.setDragonName(rs.getString("dragon_name"));
                    String enteredStr = rs.getString("entered_at");
                    if (enteredStr != null) {
                        entry.setEnteredAt(LocalDateTime.parse(enteredStr, DT_FMT));
                    }
                    entries.add(entry);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find entries for race {}", raceId, e);
        }
        return entries;
    }

    private Race mapRace(ResultSet rs) throws SQLException {
        Race race = new Race();
        race.setId(rs.getLong("id"));
        race.setTrackName(rs.getString("track_name"));
        String schedStr = rs.getString("scheduled_time");
        if (schedStr != null) {
            race.setScheduledTime(LocalDateTime.parse(schedStr, DT_FMT));
        }
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            race.setStatus(Race.Status.valueOf(statusStr));
        }
        race.setResults(rs.getString("results"));
        String createdStr = rs.getString("created_at");
        if (createdStr != null) {
            race.setCreatedAt(LocalDateTime.parse(createdStr, DT_FMT));
        }
        return race;
    }
}
