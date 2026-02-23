package com.dragonracing.leaderboard.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Bettor {

    private int rank;
    private String name;
    private int totalBets;
    private int wins;
    private int losses;
    private double earnings;
    private String favoriteDragon;

    public Bettor() {}

    public Bettor(int rank, String name, int totalBets, int wins, int losses,
                   double earnings, String favoriteDragon) {
        this.rank = rank;
        this.name = name;
        this.totalBets = totalBets;
        this.wins = wins;
        this.losses = losses;
        this.earnings = earnings;
        this.favoriteDragon = favoriteDragon;
    }

    // Getters and Setters
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getTotalBets() { return totalBets; }
    public void setTotalBets(int totalBets) { this.totalBets = totalBets; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public double getEarnings() { return earnings; }
    public void setEarnings(double earnings) { this.earnings = earnings; }

    public String getFavoriteDragon() { return favoriteDragon; }
    public void setFavoriteDragon(String favoriteDragon) { this.favoriteDragon = favoriteDragon; }
}
