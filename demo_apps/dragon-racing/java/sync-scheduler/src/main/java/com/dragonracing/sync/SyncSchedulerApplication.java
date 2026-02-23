package com.dragonracing.sync;

import com.dragonracing.sync.job.DragonAgingJob;
import com.dragonracing.sync.job.LeaderboardSyncJob;
import com.dragonracing.sync.job.StaleRaceCleanupJob;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncSchedulerApplication {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerApplication.class);

    private static final String BANNER = """

            ============================================================
                 SYNC SCHEDULER - Dragon World Background Processes
            ============================================================

                     .  *  .  *  .  *  .  *  .  *  .
                    *     TICK    *    TOCK    *     *
                   .   .-------.   .-------.   .    .
                  *    | AGING  | | LEADER |   *   *
                 .     | JOB   | | BOARD  |    .  .
                  *    '-------'  '-------'   *  *
                   .   .-------.              .  .
                    *  | STALE |  *   .  *   *  *
                     . | RACE  |   .    .  .  .
                       '-------'
                    ~~ The Gears of Fate Turn ~~

                      No HTTP Port (Background only)
                      Jobs: Aging | Sync | Cleanup
            ============================================================
            """;

    public static void main(String[] args) throws Exception {
        System.out.println(BANNER);
        log.info("Awakening the Sync Scheduler... The wheels of fate begin to turn.");

        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        // --- Dragon Aging Job (every 5 minutes) ---
        JobDetail agingJob = JobBuilder.newJob(DragonAgingJob.class)
                .withIdentity("dragonAgingJob", "dragon-world")
                .withDescription("Simulates dragon aging and training effects")
                .build();

        Trigger agingTrigger = TriggerBuilder.newTrigger()
                .withIdentity("agingTrigger", "dragon-world")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(5)
                        .repeatForever())
                .build();

        // --- Leaderboard Sync Job (every 2 minutes) ---
        JobDetail syncJob = JobBuilder.newJob(LeaderboardSyncJob.class)
                .withIdentity("leaderboardSyncJob", "dragon-world")
                .withDescription("Synchronizes leaderboard rankings")
                .build();

        Trigger syncTrigger = TriggerBuilder.newTrigger()
                .withIdentity("syncTrigger", "dragon-world")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(2)
                        .repeatForever())
                .build();

        // --- Stale Race Cleanup Job (every 10 minutes) ---
        JobDetail cleanupJob = JobBuilder.newJob(StaleRaceCleanupJob.class)
                .withIdentity("staleRaceCleanupJob", "dragon-world")
                .withDescription("Cleans up stale in-progress races")
                .build();

        Trigger cleanupTrigger = TriggerBuilder.newTrigger()
                .withIdentity("cleanupTrigger", "dragon-world")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(10)
                        .repeatForever())
                .build();

        // Schedule all jobs
        scheduler.scheduleJob(agingJob, agingTrigger);
        log.info("  Scheduled: Dragon Aging Job (every 5 min)");

        scheduler.scheduleJob(syncJob, syncTrigger);
        log.info("  Scheduled: Leaderboard Sync Job (every 2 min)");

        scheduler.scheduleJob(cleanupJob, cleanupTrigger);
        log.info("  Scheduled: Stale Race Cleanup Job (every 10 min)");

        // Start the scheduler
        scheduler.start();

        log.info("Sync Scheduler is RUNNING! The dragon world pulses with life.");
        log.info("Press Ctrl+C to stop the scheduler.");

        // Keep the main thread alive
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Shutting down the Sync Scheduler... The gears slow to a halt.");
                scheduler.shutdown(true);
                log.info("Sync Scheduler stopped. The dragon world sleeps.");
            } catch (SchedulerException e) {
                log.error("Error shutting down scheduler", e);
            }
        }));

        // Block main thread
        Thread.currentThread().join();
    }
}
