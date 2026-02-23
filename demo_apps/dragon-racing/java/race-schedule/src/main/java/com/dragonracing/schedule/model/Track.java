package com.dragonracing.schedule.model;

public class Track {

    private String name;
    private String terrain;
    private String difficulty;
    private int lengthKm;
    private String description;

    public Track() {}

    public Track(String name, String terrain, String difficulty, int lengthKm, String description) {
        this.name = name;
        this.terrain = terrain;
        this.difficulty = difficulty;
        this.lengthKm = lengthKm;
        this.description = description;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTerrain() { return terrain; }
    public void setTerrain(String terrain) { this.terrain = terrain; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getLengthKm() { return lengthKm; }
    public void setLengthKm(int lengthKm) { this.lengthKm = lengthKm; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
