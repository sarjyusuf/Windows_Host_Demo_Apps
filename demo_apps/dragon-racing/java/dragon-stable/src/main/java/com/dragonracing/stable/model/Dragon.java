package com.dragonracing.stable.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class Dragon {

    private Long id;
    private String name;
    private String breed;
    private int speed;
    private int stamina;
    private int agility;
    private int firepower;
    private int luck;
    private int wins;
    private int losses;
    private int level;
    private String ownerName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public Dragon() {
    }

    public Dragon(String name, String breed, int speed, int stamina, int agility,
                   int firepower, int luck, String ownerName) {
        this.name = name;
        this.breed = breed;
        this.speed = speed;
        this.stamina = stamina;
        this.agility = agility;
        this.firepower = firepower;
        this.luck = luck;
        this.wins = 0;
        this.losses = 0;
        this.level = 1;
        this.ownerName = ownerName;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBreed() { return breed; }
    public void setBreed(String breed) { this.breed = breed; }

    public int getSpeed() { return speed; }
    public void setSpeed(int speed) { this.speed = speed; }

    public int getStamina() { return stamina; }
    public void setStamina(int stamina) { this.stamina = stamina; }

    public int getAgility() { return agility; }
    public void setAgility(int agility) { this.agility = agility; }

    public int getFirepower() { return firepower; }
    public void setFirepower(int firepower) { this.firepower = firepower; }

    public int getLuck() { return luck; }
    public void setLuck(int luck) { this.luck = luck; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getTotalPower() {
        return speed + stamina + agility + firepower + luck;
    }

    @Override
    public String toString() {
        return "Dragon{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", breed='" + breed + '\'' +
                ", level=" + level +
                ", totalPower=" + getTotalPower() +
                '}';
    }
}
