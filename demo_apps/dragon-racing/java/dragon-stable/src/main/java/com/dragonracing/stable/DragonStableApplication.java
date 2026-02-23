package com.dragonracing.stable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DragonStableApplication {

    private static final Logger log = LoggerFactory.getLogger(DragonStableApplication.class);

    private static final String BANNER = """

            ============================================================
                        DRAGON STABLE - Dragon Management API
            ============================================================
                            ______________
                           /              \\
                          /   ()      ()   \\
                         |     ________     |
                          \\   /  FIRE  \\   /
                           | |  DRAKE  | |
                     /\\    | |_________| |    /\\
                    /  \\   \\______________/   /  \\
                   / /\\ \\       |    |       / /\\ \\
                  / /  \\ \\      |    |      / /  \\ \\
                 / /    \\ \\    /|    |\\    / /    \\ \\
                /_/      \\_\\  / |    | \\  /_/      \\_\\
                           \\_/  \\__/  \\_/
                     ~~ Where Dragons Are Born ~~

              Port: 9080  |  DB: dragons.db  |  API: /api/dragons
            ============================================================
            """;

    public static void main(String[] args) {
        System.out.println(BANNER);
        log.info("Igniting the Dragon Stable...");
        SpringApplication.run(DragonStableApplication.class, args);
        log.info("Dragon Stable is ONLINE! The dragons await their trainers.");
    }
}
