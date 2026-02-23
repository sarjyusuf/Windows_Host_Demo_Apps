package com.dragonracing.leaderboard.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class DragonRanking {

    private int rank;
    private Long dragonId;
    private String name;
    private String breed;
    private int wins;
    private int losses;
    private int totalPower;
    private int level;
    private double winRate;
    private int points;

    public DragonRanking() {}

    public DragonRanking(int rank, Long dragonId, String name, String breed,
                          int wins, int losses, int totalPower, int level) {
        this.rank = rank;
        this.dragonId = dragonId;
        this.name = name;
        this.breed = breed;
        this.wins = wins;
        this.losses = losses;
        this.totalPower = totalPower;
        this.level = level;

        int totalRaces = wins + losses;
        this.winRate = totalRaces > 0 ? (double) wins / totalRaces * 100.0 : 0.0;
        this.points = (wins * 100) + (totalPower * 2) + (level * 10);
    }

    // Getters and Setters
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Long getDragonId() { return dragonId; }
    public void setDragonId(Long dragonId) { this.dragonId = dragonId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getTotalPower() { return totalPower; }
    public void setTotalPower(int totalPower) { this.totalPower = totalPower; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
}
