package com.dragonracing.sync.config;

/**
 * Central configuration for all Dragon Racing League service endpoints.
 */
public final class ServiceEndpoints {

    private ServiceEndpoints() {}

    public static final String DRAGON_STABLE_BASE = "http://localhost:9080";
    public static final String DRAGON_STABLE_DRAGONS = DRAGON_STABLE_BASE + "/api/dragons";
    public static final String DRAGON_STABLE_HEALTH = DRAGON_STABLE_BASE + "/health";

    public static final String RACE_SCHEDULE_BASE = "http://localhost:9081";
    public static final String RACE_SCHEDULE_RACES = RACE_SCHEDULE_BASE + "/api/races";
    public static final String RACE_SCHEDULE_HEALTH = RACE_SCHEDULE_BASE + "/health";

    public static final String LEADERBOARD_BASE = "http://localhost:9082";
    public static final String LEADERBOARD_SYNC = LEADERBOARD_BASE + "/api/leaderboard/sync";
    public static final String LEADERBOARD_HEALTH = LEADERBOARD_BASE + "/health";

    public static final String MEDIA_SERVICE_BASE = "http://localhost:9083";
    public static final String MEDIA_SERVICE_HEALTH = MEDIA_SERVICE_BASE + "/health";
}
