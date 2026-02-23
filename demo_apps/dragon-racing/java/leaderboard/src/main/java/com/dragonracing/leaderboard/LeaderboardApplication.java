package com.dragonracing.leaderboard;

import io.micronaut.runtime.Micronaut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeaderboardApplication {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardApplication.class);

    private static final String BANNER = """

            ============================================================
                   LEADERBOARD - Dragon Racing Rankings & Achievements
            ============================================================

                         .     .     .     .     .
                    *  .  |1st|  |2nd|  |3rd|  .  *
                   .     _|___|_ |___| _|___|    .
                  *     |       ||   ||       |    *
                 .      | GOLD  ||SIL|| BRONZE|     .
                  *     |       ||VER||       |    *
                   .    |_______||___||_______|   .
                    *  ===========================  *
                      ~~~ The Hall of Champions ~~~

                 Port: 9082  |  API: /api/leaderboard
            ============================================================
            """;

    public static void main(String[] args) {
        System.out.println(BANNER);
        log.info("Opening the Hall of Champions...");
        Micronaut.run(LeaderboardApplication.class, args);
        log.info("Leaderboard service is ONLINE! Glory awaits the victorious!");
    }
}
