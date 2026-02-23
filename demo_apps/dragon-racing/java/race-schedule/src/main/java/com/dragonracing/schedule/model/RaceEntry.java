package com.dragonracing.schedule.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public class RaceEntry {

    private Long id;
    private Long raceId;
    private Long dragonId;
    private String dragonName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime enteredAt;

    public RaceEntry() {
        this.enteredAt = LocalDateTime.now();
    }

    public RaceEntry(Long raceId, Long dragonId, String dragonName) {
        this();
        this.raceId = raceId;
        this.dragonId = dragonId;
        this.dragonName = dragonName;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaceId() { return raceId; }
    public void setRaceId(Long raceId) { this.raceId = raceId; }

    public Long getDragonId() { return dragonId; }
    public void setDragonId(Long dragonId) { this.dragonId = dragonId; }

    public String getDragonName() { return dragonName; }
    public void setDragonName(String dragonName) { this.dragonName = dragonName; }

    public LocalDateTime getEnteredAt() { return enteredAt; }
    public void setEnteredAt(LocalDateTime enteredAt) { this.enteredAt = enteredAt; }
}
