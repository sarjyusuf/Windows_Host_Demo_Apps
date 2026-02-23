package com.dragonracing.stable.model;

public class DragonStats {

    private Long dragonId;
    private String name;
    private String breed;
    private int level;
    private int speed;
    private int stamina;
    private int agility;
    private int firepower;
    private int luck;
    private int totalPower;
    private int wins;
    private int losses;
    private double winRate;
    private String rank;
    private String specialAbility;

    public DragonStats(Dragon dragon) {
        this.dragonId = dragon.getId();
        this.name = dragon.getName();
        this.breed = dragon.getBreed();
        this.level = dragon.getLevel();
        this.speed = dragon.getSpeed();
        this.stamina = dragon.getStamina();
        this.agility = dragon.getAgility();
        this.firepower = dragon.getFirepower();
        this.luck = dragon.getLuck();
        this.totalPower = dragon.getTotalPower();
        this.wins = dragon.getWins();
        this.losses = dragon.getLosses();

        int totalRaces = wins + losses;
        this.winRate = totalRaces > 0 ? (double) wins / totalRaces * 100.0 : 0.0;
        this.rank = calculateRank(level, totalPower);
        this.specialAbility = getBreedAbility(breed);
    }

    private String calculateRank(int level, int totalPower) {
        if (level >= 20 && totalPower >= 80) return "Legendary";
        if (level >= 15 && totalPower >= 60) return "Epic";
        if (level >= 10 && totalPower >= 45) return "Rare";
        if (level >= 5 && totalPower >= 30) return "Uncommon";
        return "Common";
    }

    private String getBreedAbility(String breed) {
        return switch (breed) {
            case "Fire Drake" -> "Flame Burst - +3 speed boost in hot tracks";
            case "Ice Wyrm" -> "Frost Shield - +3 stamina in cold conditions";
            case "Storm Serpent" -> "Lightning Dash - +3 agility during storms";
            case "Shadow Dragon" -> "Dark Veil - +3 luck in night races";
            case "Thunder Beast" -> "Thunder Charge - +3 firepower in open fields";
            case "Void Walker" -> "Phase Shift - +2 to all stats in dimensional tracks";
            default -> "No special ability";
        };
    }

    // Getters
    public Long getDragonId() { return dragonId; }
    public String getName() { return name; }
    public String getBreed() { return breed; }
    public int getLevel() { return level; }
    public int getSpeed() { return speed; }
    public int getStamina() { return stamina; }
    public int getAgility() { return agility; }
    public int getFirepower() { return firepower; }
    public int getLuck() { return luck; }
    public int getTotalPower() { return totalPower; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public double getWinRate() { return winRate; }
    public String getRank() { return rank; }
    public String getSpecialAbility() { return specialAbility; }
}
