package com.dragonracing.schedule;

import com.dragonracing.schedule.config.RaceScheduleConfiguration;
import com.dragonracing.schedule.db.RaceDatabase;
import com.dragonracing.schedule.resource.RaceResource;
import com.dragonracing.schedule.resource.TrackResource;
import com.dragonracing.schedule.resource.HealthResource;
import com.dragonracing.schedule.queue.RaceQueueWriter;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RaceScheduleApplication extends Application<RaceScheduleConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(RaceScheduleApplication.class);

    private static final String BANNER = """

            ============================================================
                     RACE SCHEDULE - Dragon Race Management
            ============================================================

                    .     *  .    *    .   *   .     .  *
                 *    .       .      .     .     *      .
                    .   /\\_/\\   .     .   *   .    .   *
                   *   / o o \\    *    .     .    *  .
                  .   ( =^.^= )     .    *    .    .
                 *  .  )     (   .    .    .   *     *
                  .   (  | |  )    *    .       .  .
                   *   \\_| |_/   .    .    *     *
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                     ~~ Track Your Dragon Races ~~

               Port: 9081  |  Admin: 9181  |  API: /api/races
            ============================================================
            """;

    public static void main(String[] args) throws Exception {
        System.out.println(BANNER);
        log.info("Firing up the Race Schedule service...");
        new RaceScheduleApplication().run(args);
    }

    @Override
    public String getName() {
        return "race-schedule";
    }

    @Override
    public void initialize(Bootstrap<RaceScheduleConfiguration> bootstrap) {
        // Dropwizard initialization
    }

    @Override
    public void run(RaceScheduleConfiguration config, Environment environment) {
        log.info("Initializing Race Schedule with database: {}", config.getDatabasePath());

        // Initialize database
        RaceDatabase raceDb = new RaceDatabase(config.getDatabasePath());
        raceDb.initialize();

        // Initialize queue writer
        RaceQueueWriter queueWriter = new RaceQueueWriter(config.getQueueDir());

        // Register resources
        environment.jersey().register(new RaceResource(raceDb, queueWriter));
        environment.jersey().register(new TrackResource());
        environment.jersey().register(new HealthResource(raceDb));

        log.info("Race Schedule is LIVE! May the fastest dragon win!");
    }
}
