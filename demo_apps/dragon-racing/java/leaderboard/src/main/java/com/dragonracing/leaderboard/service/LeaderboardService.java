package com.dragonracing.leaderboard.service;

import com.dragonracing.leaderboard.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);

    @Value("${dragon-stable.url:http://localhost:9080}")
    private String dragonStableUrl;

    private final Map<Long, DragonRanking> rankings = new ConcurrentHashMap<>();
    private final Map<Long, List<Achievement>> achievements = new ConcurrentHashMap<>();
    private final List<Bettor> topBettors = new ArrayList<>();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LeaderboardService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
        initializeSampleBettors();
    }

    @EventListener
    public void onStartup(StartupEvent event) {
        log.info("Leaderboard starting up - syncing from Dragon Stable...");
        syncFromDragonStable();
    }

    public void syncFromDragonStable() {
        try {
            String url = dragonStableUrl + "/api/dragons";
            log.info("Syncing dragon data from: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode dragonsArray = objectMapper.readTree(response.body());
                rankings.clear();
                achievements.clear();

                List<DragonRanking> tempRankings = new ArrayList<>();

                for (JsonNode dragonNode : dragonsArray) {
                    long id = dragonNode.get("id").asLong();
                    String name = dragonNode.get("name").asText();
                    String breed = dragonNode.get("breed").asText();
                    int wins = dragonNode.get("wins").asInt();
                    int losses = dragonNode.get("losses").asInt();
                    int speed = dragonNode.get("speed").asInt();
                    int stamina = dragonNode.get("stamina").asInt();
                    int agility = dragonNode.get("agility").asInt();
                    int firepower = dragonNode.get("firepower").asInt();
                    int luck = dragonNode.get("luck").asInt();
                    int level = dragonNode.get("level").asInt();
                    int totalPower = speed + stamina + agility + firepower + luck;

                    tempRankings.add(new DragonRanking(0, id, name, breed, wins, losses, totalPower, level));

                    // Generate achievements
                    achievements.put(id, generateAchievements(id, name, breed, wins, losses, totalPower, level,
                            speed, stamina, agility, firepower, luck));
                }

                // Sort by points descending and assign ranks
                tempRankings.sort((a, b) -> Integer.compare(b.getPoints(), a.getPoints()));
                for (int i = 0; i < tempRankings.size(); i++) {
                    tempRankings.get(i).setRank(i + 1);
                    rankings.put(tempRankings.get(i).getDragonId(), tempRankings.get(i));
                }

                log.info("Synced {} dragons from Dragon Stable. Rankings updated!", rankings.size());
            } else {
                log.warn("Failed to sync from Dragon Stable: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            log.warn("Could not sync from Dragon Stable (is it running?): {}", e.getMessage());
        }
    }

    public List<DragonRanking> getLeaderboard() {
        return rankings.values().stream()
                .sorted(Comparator.comparingInt(DragonRanking::getRank))
                .collect(Collectors.toList());
    }

    public List<SeasonStanding> getSeasonStandings() {
        List<SeasonStanding> standings = new ArrayList<>();
        int position = 1;
        for (DragonRanking r : getLeaderboard()) {
            standings.add(new SeasonStanding(
                    position++, r.getDragonId(), r.getName(), r.getBreed(),
                    r.getWins(), r.getLosses(), r.getPoints()));
        }
        return standings;
    }

    public List<Bettor> getTopBettors() {
        return topBettors;
    }

    public List<Achievement> getAchievements(Long dragonId) {
        return achievements.getOrDefault(dragonId, List.of());
    }

    private List<Achievement> generateAchievements(Long id, String name, String breed,
                                                     int wins, int losses, int totalPower, int level,
                                                     int speed, int stamina, int agility,
                                                     int firepower, int luck) {
        List<Achievement> achs = new ArrayList<>();

        // First Win
        achs.add(new Achievement(
                "First Win",
                "Win your first race",
                "TROPHY",
                wins >= 1));

        // Speed Demon
        achs.add(new Achievement(
                "Speed Demon",
                "Reach 15+ speed stat",
                "LIGHTNING",
                speed >= 15));

        // Iron Scales
        achs.add(new Achievement(
                "Iron Scales",
                "Reach 15+ stamina stat",
                "SHIELD",
                stamina >= 15));

        // Wind Dancer
        achs.add(new Achievement(
                "Wind Dancer",
                "Reach 15+ agility stat",
                "WIND",
                agility >= 15));

        // Inferno Breath
        achs.add(new Achievement(
                "Inferno Breath",
                "Reach 15+ firepower stat",
                "FLAME",
                firepower >= 15));

        // Fortune's Favorite
        achs.add(new Achievement(
                "Fortune's Favorite",
                "Reach 15+ luck stat",
                "CLOVER",
                luck >= 15));

        // Power Overwhelming
        achs.add(new Achievement(
                "Power Overwhelming",
                "Reach 60+ total power",
                "STAR",
                totalPower >= 60));

        // Veteran
        achs.add(new Achievement(
                "Veteran",
                "Compete in 10+ races",
                "MEDAL",
                (wins + losses) >= 10));

        // Champion
        achs.add(new Achievement(
                "Champion",
                "Win 5+ races",
                "CROWN",
                wins >= 5));

        // Undefeated
        achs.add(new Achievement(
                "Undefeated",
                "Have more wins than losses with at least 3 wins",
                "FIRE",
                wins > losses && wins >= 3));

        // Legendary
        achs.add(new Achievement(
                "Legendary",
                "Reach level 10+",
                "DRAGON",
                level >= 10));

        // Rising Star
        achs.add(new Achievement(
                "Rising Star",
                "A new dragon enters the league",
                "SPARKLE",
                true)); // Everyone gets this

        return achs;
    }

    private void initializeSampleBettors() {
        topBettors.add(new Bettor(1, "Goldcoin Gary", 47, 31, 16, 15200.50, "Shadowfang"));
        topBettors.add(new Bettor(2, "Lucky Luna", 52, 28, 24, 8750.00, "Emberclaw"));
        topBettors.add(new Bettor(3, "Highroller Hank", 38, 22, 16, 6200.75, "Thunderwing"));
        topBettors.add(new Bettor(4, "Shrewd Sheila", 65, 30, 35, 4100.25, "Frostbite"));
        topBettors.add(new Bettor(5, "Daring Dave", 29, 18, 11, 3800.00, "Nightshade"));
        topBettors.add(new Bettor(6, "Mystic Mira", 41, 20, 21, 2500.50, "Venomspire"));
        topBettors.add(new Bettor(7, "Baron von Bet", 55, 24, 31, 1200.00, "Blazeheart"));
        topBettors.add(new Bettor(8, "Penny Pete", 88, 35, 53, 800.75, "Stormscale"));
    }
}
