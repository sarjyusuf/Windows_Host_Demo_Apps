package com.dragonracing.leaderboard.controller;

import com.dragonracing.leaderboard.model.Achievement;
import com.dragonracing.leaderboard.model.Bettor;
import com.dragonracing.leaderboard.model.DragonRanking;
import com.dragonracing.leaderboard.model.SeasonStanding;
import com.dragonracing.leaderboard.service.LeaderboardService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class LeaderboardController {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardController.class);
    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @Get("/api/leaderboard")
    public HttpResponse<Map<String, Object>> getLeaderboard() {
        log.info("Fetching dragon leaderboard...");
        List<DragonRanking> rankings = leaderboardService.getLeaderboard();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", "Dragon Racing League - Official Rankings");
        response.put("totalDragons", rankings.size());
        response.put("rankings", rankings);

        return HttpResponse.ok(response);
    }

    @Get("/api/leaderboard/season")
    public HttpResponse<Map<String, Object>> getSeasonStandings() {
        log.info("Fetching current season standings...");
        List<SeasonStanding> standings = leaderboardService.getSeasonStandings();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("season", "Season 1 - The Age of Fire");
        response.put("standings", standings);

        return HttpResponse.ok(response);
    }

    @Get("/api/leaderboard/bettors")
    public HttpResponse<Map<String, Object>> getTopBettors() {
        log.info("Fetching top bettors...");
        List<Bettor> bettors = leaderboardService.getTopBettors();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", "Top Dragon Race Bettors");
        response.put("bettors", bettors);

        return HttpResponse.ok(response);
    }

    @Post("/api/leaderboard/sync")
    public HttpResponse<Map<String, Object>> triggerSync() {
        log.info("Manual sync triggered...");
        leaderboardService.syncFromDragonStable();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "synced");
        response.put("message", "Leaderboard synced from Dragon Stable!");
        response.put("rankings", leaderboardService.getLeaderboard().size());

        return HttpResponse.ok(response);
    }

    @Get("/api/achievements/{dragonId}")
    public HttpResponse<?> getAchievements(@PathVariable Long dragonId) {
        log.info("Fetching achievements for dragon {}...", dragonId);
        List<Achievement> achs = leaderboardService.getAchievements(dragonId);

        if (achs.isEmpty()) {
            return HttpResponse.notFound(Map.of(
                    "error", "No achievements found for dragon " + dragonId,
                    "hint", "Try triggering a sync first: POST /api/leaderboard/sync"));
        }

        long unlocked = achs.stream().filter(Achievement::isUnlocked).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("dragonId", dragonId);
        response.put("totalAchievements", achs.size());
        response.put("unlocked", unlocked);
        response.put("locked", achs.size() - unlocked);
        response.put("achievements", achs);

        return HttpResponse.ok(response);
    }

    @Get("/health")
    public HttpResponse<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "leaderboard");
        health.put("rankedDragons", leaderboardService.getLeaderboard().size());
        health.put("timestamp", System.currentTimeMillis());
        return HttpResponse.ok(health);
    }
}
