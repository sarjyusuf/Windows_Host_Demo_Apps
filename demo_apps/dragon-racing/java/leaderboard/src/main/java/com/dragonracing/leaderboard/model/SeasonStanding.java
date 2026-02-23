package com.dragonracing.leaderboard.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class SeasonStanding {

    private int position;
    private Long dragonId;
    private String dragonName;
    private String breed;
    private int seasonWins;
    private int seasonLosses;
    private int seasonPoints;
    private String tier; // Bronze, Silver, Gold, Platinum, Diamond

    public SeasonStanding() {}

    public SeasonStanding(int position, Long dragonId, String dragonName, String breed,
                           int seasonWins, int seasonLosses, int seasonPoints) {
        this.position = position;
        this.dragonId = dragonId;
        this.dragonName = dragonName;
        this.breed = breed;
        this.seasonWins = seasonWins;
        this.seasonLosses = seasonLosses;
        this.seasonPoints = seasonPoints;
        this.tier = calculateTier(seasonPoints);
    }

    private String calculateTier(int points) {
        if (points >= 5000) return "Diamond";
        if (points >= 3000) return "Platinum";
        if (points >= 1500) return "Gold";
        if (points >= 500) return "Silver";
        return "Bronze";
    }

    // Getters and Setters
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    public Long getDragonId() { return dragonId; }
    public void setDragonId(Long dragonId) { this.dragonId = dragonId; }

    public String getDragonName() { return dragonName; }
    public void setDragonName(String dragonName) { this.dragonName = dragonName; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public int getSeasonWins() { return seasonWins; }
    public void setSeasonWins(int seasonWins) { this.seasonWins = seasonWins; }

    public int getSeasonLosses() { return seasonLosses; }
    public void setSeasonLosses(int seasonLosses) { this.seasonLosses = seasonLosses; }

    public int getSeasonPoints() { return seasonPoints; }
    public void setSeasonPoints(int seasonPoints) { this.seasonPoints = seasonPoints; }

    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
}
