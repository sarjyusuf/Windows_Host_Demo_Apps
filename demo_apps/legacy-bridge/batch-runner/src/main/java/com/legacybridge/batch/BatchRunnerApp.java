package com.legacybridge.batch;

import com.legacybridge.batch.config.SchedulerConfig;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Batch Runner Application - Standalone JAR running Quartz scheduler for periodic
 * maintenance tasks. Connects to ActiveMQ and HTTP services for document management.
 *
 * Scheduled Jobs:
 * - CleanupJob: runs every 5 minutes, removes old processed documents
 * - ReindexJob: runs every 10 minutes, triggers reindex of all documents
 * - HealthCheckJob: runs every 1 minute, pings all services
 *
 * Runs until SIGTERM is received.
 */
public class BatchRunnerApp {

    private static final Logger logger = LoggerFactory.getLogger(BatchRunnerApp.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting LegacyBridge Batch Runner");
        logger.info("========================================");
        logger.info("Quartz Scheduler with periodic maintenance jobs");

        try {
            // Build and configure the scheduler with all jobs
            SchedulerConfig schedulerConfig = new SchedulerConfig();
            Scheduler scheduler = schedulerConfig.buildScheduler();

            // Register shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received. Stopping Batch Runner...");
                try {
                    scheduler.shutdown(true); // true = wait for jobs to complete
                    logger.info("Quartz Scheduler shut down gracefully.");
                } catch (SchedulerException e) {
                    logger.error("Error shutting down scheduler: {}", e.getMessage(), e);
                }
                logger.info("Batch Runner stopped.");
            }));

            // Start the scheduler
            scheduler.start();

            logger.info("========================================");
            logger.info("Batch Runner started successfully");
            logger.info("Scheduled Jobs:");
            logger.info("  - CleanupJob:     every 5 minutes");
            logger.info("  - ReindexJob:     every 10 minutes");
            logger.info("  - HealthCheckJob: every 1 minute");
            logger.info("========================================");

            // Keep the main thread alive until SIGTERM
            // The scheduler runs in its own thread pool
            Thread.currentThread().join();

        } catch (SchedulerException e) {
            logger.error("Failed to start Quartz Scheduler: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            logger.info("Main thread interrupted. Exiting.");
            Thread.currentThread().interrupt();
        }
    }
}
