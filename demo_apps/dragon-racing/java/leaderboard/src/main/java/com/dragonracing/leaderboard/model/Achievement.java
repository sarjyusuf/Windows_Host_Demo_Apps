package com.dragonracing.leaderboard.model;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Achievement {

    private String name;
    private String description;
    private String icon;
    private boolean unlocked;

    public Achievement() {}

    public Achievement(String name, String description, String icon, boolean unlocked) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.unlocked = unlocked;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isUnlocked() { return unlocked; }
    public void setUnlocked(boolean unlocked) { this.unlocked = unlocked; }
}
