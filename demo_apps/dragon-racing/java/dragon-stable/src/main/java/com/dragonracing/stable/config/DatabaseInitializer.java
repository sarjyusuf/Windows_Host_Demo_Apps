package com.dragonracing.stable.config;

import com.dragonracing.stable.model.Dragon;
import com.dragonracing.stable.repository.DragonRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final DragonRepository dragonRepository;

    public DatabaseInitializer(DragonRepository dragonRepository) {
        this.dragonRepository = dragonRepository;
    }

    @PostConstruct
    public void initialize() {
        // Ensure data directory exists
        File dataDir = new File("C:\\dragon-racing\\data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
            log.info("Created data directory: {}", dataDir.getAbsolutePath());
        }

        dragonRepository.createTable();

        if (dragonRepository.count() == 0) {
            log.info("No dragons found. Hatching the initial brood...");
            seedDragons();
        } else {
            log.info("Dragon stable already populated with {} dragons.", dragonRepository.count());
        }
    }

    private void seedDragons() {
        Dragon[] dragons = {
            new Dragon("Shadowfang", "Shadow Dragon", 12, 8, 14, 7, 10, "Lord Obsidian"),
            new Dragon("Emberclaw", "Fire Drake", 10, 9, 8, 15, 6, "Pyra the Flamecaller"),
            new Dragon("Frostbite", "Ice Wyrm", 8, 14, 9, 6, 11, "Winter Queen Elara"),
            new Dragon("Thunderwing", "Thunder Beast", 11, 10, 12, 13, 5, "Stormmaster Kael"),
            new Dragon("Nightshade", "Void Walker", 9, 11, 10, 10, 13, "The Void Whisperer"),
            new Dragon("Blazeheart", "Fire Drake", 14, 7, 9, 14, 8, "Ignis Rex"),
            new Dragon("Stormscale", "Storm Serpent", 13, 8, 15, 9, 7, "Captain Tempest"),
            new Dragon("Venomspire", "Shadow Dragon", 10, 12, 11, 8, 12, "Duchess Nightfall")
        };

        for (Dragon dragon : dragons) {
            dragonRepository.save(dragon);
            log.info("  Hatched: {} the {} (Power: {})", dragon.getName(), dragon.getBreed(), dragon.getTotalPower());
        }

        log.info("All 8 dragons have been hatched and are ready for training!");
    }
}
