package com.dragonracing.schedule.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import jakarta.validation.constraints.NotEmpty;

public class RaceScheduleConfiguration extends Configuration {

    @NotEmpty
    private String databasePath = "C:\\dragon-racing\\data\\races.db";

    @NotEmpty
    private String queueDir = "C:\\dragon-racing\\queues\\races\\pending";

    @JsonProperty
    public String getDatabasePath() {
        return databasePath;
    }

    @JsonProperty
    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    @JsonProperty
    public String getQueueDir() {
        return queueDir;
    }

    @JsonProperty
    public void setQueueDir(String queueDir) {
        this.queueDir = queueDir;
    }
}
