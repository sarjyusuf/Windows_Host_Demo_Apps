package com.dragonracing.schedule.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Race {

    public enum Status {
        Scheduled, InProgress, Completed, Cancelled
    }

    private Long id;
    private String trackName;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledTime;

    private Status status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    private List<RaceEntry> entries = new ArrayList<>();
    private String results; // JSON string of race results

    public Race() {
        this.status = Status.Scheduled;
        this.createdAt = LocalDateTime.now();
    }

    public Race(String trackName, LocalDateTime scheduledTime) {
        this();
        this.trackName = trackName;
        this.scheduledTime = scheduledTime;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackName() { return trackName; }
    public void setTrackName(String trackName) { this.trackName = trackName; }

    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<RaceEntry> getEntries() { return entries; }
    public void setEntries(List<RaceEntry> entries) { this.entries = entries; }

    public String getResults() { return results; }
    public void setResults(String results) { this.results = results; }

    @JsonProperty("entryCount")
    public int getEntryCount() { return entries != null ? entries.size() : 0; }
}
