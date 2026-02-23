package com.dragonracing.stable.controller;

import com.dragonracing.stable.model.BreedRequest;
import com.dragonracing.stable.model.Dragon;
import com.dragonracing.stable.model.DragonStats;
import com.dragonracing.stable.repository.DragonRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
public class DragonController {

    private static final Logger log = LoggerFactory.getLogger(DragonController.class);
    private final DragonRepository dragonRepository;

    private static final List<String> BREEDS = List.of(
            "Fire Drake", "Ice Wyrm", "Storm Serpent",
            "Shadow Dragon", "Thunder Beast", "Void Walker"
    );

    private static final String[] STAT_NAMES = {"speed", "stamina", "agility", "firepower", "luck"};

    public DragonController(DragonRepository dragonRepository) {
        this.dragonRepository = dragonRepository;
    }

    @GetMapping("/api/dragons")
    public ResponseEntity<List<Dragon>> listDragons() {
        log.info("Listing all dragons in the stable...");
        return ResponseEntity.ok(dragonRepository.findAll());
    }

    @GetMapping("/api/dragons/{id}")
    public ResponseEntity<?> getDragon(@PathVariable Long id) {
        return dragonRepository.findById(id)
                .map(dragon -> {
                    log.info("Found dragon: {}", dragon.getName());
                    return ResponseEntity.ok((Object) dragon);
                })
                .orElseGet(() -> {
                    log.warn("Dragon {} not found in the stable!", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Dragon not found", "dragonId", id));
                });
    }

    @PostMapping("/api/dragons")
    public ResponseEntity<?> createDragon(@RequestBody Dragon dragon) {
        if (dragon.getName() == null || dragon.getName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Dragon must have a name!"));
        }
        if (dragon.getBreed() == null || !BREEDS.contains(dragon.getBreed())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid breed. Choose from: " + BREEDS,
                    "validBreeds", BREEDS));
        }

        // Set defaults if not provided
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (dragon.getSpeed() <= 0) dragon.setSpeed(rng.nextInt(5, 12));
        if (dragon.getStamina() <= 0) dragon.setStamina(rng.nextInt(5, 12));
        if (dragon.getAgility() <= 0) dragon.setAgility(rng.nextInt(5, 12));
        if (dragon.getFirepower() <= 0) dragon.setFirepower(rng.nextInt(5, 12));
        if (dragon.getLuck() <= 0) dragon.setLuck(rng.nextInt(5, 12));
        dragon.setLevel(1);
        dragon.setWins(0);
        dragon.setLosses(0);

        Dragon saved = dragonRepository.save(dragon);
        log.info("A new dragon has been born! Welcome {} the {} (Power: {})",
                saved.getName(), saved.getBreed(), saved.getTotalPower());

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/api/dragons/{id}/train")
    public ResponseEntity<?> trainDragon(@PathVariable Long id) {
        Optional<Dragon> opt = dragonRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Dragon not found"));
        }

        Dragon dragon = opt.get();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Pick a random stat to increase
        int statIndex = rng.nextInt(STAT_NAMES.length);
        int increase = rng.nextInt(1, 4); // 1-3

        String trainedStat = STAT_NAMES[statIndex];
        switch (statIndex) {
            case 0 -> dragon.setSpeed(dragon.getSpeed() + increase);
            case 1 -> dragon.setStamina(dragon.getStamina() + increase);
            case 2 -> dragon.setAgility(dragon.getAgility() + increase);
            case 3 -> dragon.setFirepower(dragon.getFirepower() + increase);
            case 4 -> dragon.setLuck(dragon.getLuck() + increase);
        }

        // Level up check
        int totalPower = dragon.getTotalPower();
        int newLevel = 1 + (totalPower / 10);
        dragon.setLevel(newLevel);

        dragonRepository.update(dragon);
        log.info("{} trained hard! {} increased by {} (now level {})",
                dragon.getName(), trainedStat, increase, dragon.getLevel());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dragon", dragon);
        result.put("trainedStat", trainedStat);
        result.put("increase", increase);
        result.put("message", String.format("%s trained %s and gained +%d! (Level %d)",
                dragon.getName(), trainedStat, increase, dragon.getLevel()));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/dragons/breed")
    public ResponseEntity<?> breedDragons(@RequestBody BreedRequest request) {
        if (request.getParentOneId() == null || request.getParentTwoId() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Both parentOneId and parentTwoId are required"));
        }
        if (request.getParentOneId().equals(request.getParentTwoId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A dragon cannot breed with itself!"));
        }

        Optional<Dragon> p1Opt = dragonRepository.findById(request.getParentOneId());
        Optional<Dragon> p2Opt = dragonRepository.findById(request.getParentTwoId());

        if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "One or both parent dragons not found"));
        }

        Dragon parent1 = p1Opt.get();
        Dragon parent2 = p2Opt.get();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Mix stats from both parents with random variation
        int speed = mixStat(parent1.getSpeed(), parent2.getSpeed(), rng);
        int stamina = mixStat(parent1.getStamina(), parent2.getStamina(), rng);
        int agility = mixStat(parent1.getAgility(), parent2.getAgility(), rng);
        int firepower = mixStat(parent1.getFirepower(), parent2.getFirepower(), rng);
        int luck = mixStat(parent1.getLuck(), parent2.getLuck(), rng);

        // Breed is randomly chosen from either parent or a new random breed
        String breed;
        int breedRoll = rng.nextInt(10);
        if (breedRoll < 4) {
            breed = parent1.getBreed();
        } else if (breedRoll < 8) {
            breed = parent2.getBreed();
        } else {
            breed = BREEDS.get(rng.nextInt(BREEDS.size()));
        }

        String name = request.getOffspringName() != null ? request.getOffspringName()
                : generateOffspringName(parent1.getName(), parent2.getName());
        String owner = request.getOwnerName() != null ? request.getOwnerName() : "The Racing League";

        Dragon offspring = new Dragon(name, breed, speed, stamina, agility, firepower, luck, owner);
        Dragon saved = dragonRepository.save(offspring);

        log.info("A new dragon has been bred! {} the {} from parents {} and {} (Power: {})",
                saved.getName(), saved.getBreed(), parent1.getName(), parent2.getName(), saved.getTotalPower());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("offspring", saved);
        result.put("parentOne", parent1.getName());
        result.put("parentTwo", parent2.getName());
        result.put("message", String.format("The union of %s and %s has produced %s the %s!",
                parent1.getName(), parent2.getName(), saved.getName(), saved.getBreed()));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/api/dragons/{id}/stats")
    public ResponseEntity<?> getDragonStats(@PathVariable Long id) {
        return dragonRepository.findById(id)
                .map(dragon -> {
                    DragonStats stats = new DragonStats(dragon);
                    log.info("Stats requested for {} (Rank: {})", dragon.getName(), stats.getRank());
                    return ResponseEntity.ok((Object) stats);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Dragon not found")));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "dragon-stable");
        health.put("dragonCount", dragonRepository.count());
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }

    private int mixStat(int stat1, int stat2, ThreadLocalRandom rng) {
        int base = (stat1 + stat2) / 2;
        int variation = rng.nextInt(-3, 4); // -3 to +3
        return Math.max(1, base + variation);
    }

    private String generateOffspringName(String parent1Name, String parent2Name) {
        // Take prefix from one parent and suffix from the other
        String prefix = parent1Name.substring(0, Math.min(4, parent1Name.length()));
        String suffix = parent2Name.substring(Math.max(0, parent2Name.length() - 4));
        return prefix + suffix;
    }
}
