package com.dragonracing.stable.repository;

import com.dragonracing.stable.model.Dragon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Repository
public class DragonRepository {

    private static final Logger log = LoggerFactory.getLogger(DragonRepository.class);
    private final JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public DragonRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Dragon> dragonRowMapper = (ResultSet rs, int rowNum) -> {
        Dragon d = new Dragon();
        d.setId(rs.getLong("id"));
        d.setName(rs.getString("name"));
        d.setBreed(rs.getString("breed"));
        d.setSpeed(rs.getInt("speed"));
        d.setStamina(rs.getInt("stamina"));
        d.setAgility(rs.getInt("agility"));
        d.setFirepower(rs.getInt("firepower"));
        d.setLuck(rs.getInt("luck"));
        d.setWins(rs.getInt("wins"));
        d.setLosses(rs.getInt("losses"));
        d.setLevel(rs.getInt("level"));
        d.setOwnerName(rs.getString("owner_name"));
        String createdStr = rs.getString("created_at");
        if (createdStr != null) {
            d.setCreatedAt(LocalDateTime.parse(createdStr, DT_FMT));
        }
        return d;
    };

    public void createTable() {
        log.info("Creating dragons table if not exists...");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS dragons (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                breed TEXT NOT NULL,
                speed INTEGER DEFAULT 5,
                stamina INTEGER DEFAULT 5,
                agility INTEGER DEFAULT 5,
                firepower INTEGER DEFAULT 5,
                luck INTEGER DEFAULT 5,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                level INTEGER DEFAULT 1,
                owner_name TEXT,
                created_at TEXT
            )
        """);
    }

    public List<Dragon> findAll() {
        return jdbcTemplate.query("SELECT * FROM dragons ORDER BY id", dragonRowMapper);
    }

    public Optional<Dragon> findById(Long id) {
        List<Dragon> results = jdbcTemplate.query(
                "SELECT * FROM dragons WHERE id = ?", dragonRowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Dragon save(Dragon dragon) {
        if (dragon.getCreatedAt() == null) {
            dragon.setCreatedAt(LocalDateTime.now());
        }
        jdbcTemplate.update("""
            INSERT INTO dragons (name, breed, speed, stamina, agility, firepower, luck,
                                 wins, losses, level, owner_name, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                dragon.getName(), dragon.getBreed(), dragon.getSpeed(), dragon.getStamina(),
                dragon.getAgility(), dragon.getFirepower(), dragon.getLuck(),
                dragon.getWins(), dragon.getLosses(), dragon.getLevel(),
                dragon.getOwnerName(), dragon.getCreatedAt().format(DT_FMT));

        // Get the last inserted id
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        dragon.setId(id);
        return dragon;
    }

    public void update(Dragon dragon) {
        jdbcTemplate.update("""
            UPDATE dragons SET name = ?, breed = ?, speed = ?, stamina = ?, agility = ?,
                firepower = ?, luck = ?, wins = ?, losses = ?, level = ?, owner_name = ?
            WHERE id = ?
            """,
                dragon.getName(), dragon.getBreed(), dragon.getSpeed(), dragon.getStamina(),
                dragon.getAgility(), dragon.getFirepower(), dragon.getLuck(),
                dragon.getWins(), dragon.getLosses(), dragon.getLevel(),
                dragon.getOwnerName(), dragon.getId());
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dragons", Long.class);
        return count != null ? count : 0;
    }
}
