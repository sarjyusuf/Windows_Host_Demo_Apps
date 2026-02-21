package com.legacybridge.batch.config;

import com.legacybridge.batch.jobs.CleanupJob;
import com.legacybridge.batch.jobs.HealthCheckJob;
import com.legacybridge.batch.jobs.ReindexJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures and builds the Quartz Scheduler with all maintenance jobs.
 * Uses StdSchedulerFactory with properties from quartz.properties resource.
 *
 * Schedules:
 * - CleanupJob: runs every 5 minutes, removes old processed documents
 * - ReindexJob: runs every 10 minutes, triggers reindex of all documents
 * - HealthCheckJob: runs every 1 minute, pings all services
 */
public class SchedulerConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfig.class);

    /**
     * Builds and configures the Quartz Scheduler with all jobs and triggers.
     *
     * @return the configured Scheduler (not yet started)
     * @throws SchedulerException if the scheduler cannot be created or jobs cannot be scheduled
     */
    public Scheduler buildScheduler() throws SchedulerException {
        logger.info("Building Quartz Scheduler...");

        // Create scheduler from factory (uses quartz.properties on classpath)
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
        logger.info("Scheduler created: {}", scheduler.getSchedulerName());

        // Schedule CleanupJob - every 5 minutes
        scheduleCleanupJob(scheduler);

        // Schedule ReindexJob - every 10 minutes
        scheduleReindexJob(scheduler);

        // Schedule HealthCheckJob - every 1 minute
        scheduleHealthCheckJob(scheduler);

        logger.info("All jobs scheduled successfully");
        return scheduler;
    }

    /**
     * Schedules the CleanupJob to run every 5 minutes.
     * This job removes old processed documents that are past their retention period.
     */
    private void scheduleCleanupJob(Scheduler scheduler) throws SchedulerException {
        logger.info("Scheduling CleanupJob (every 5 minutes)");

        JobDetail jobDetail = JobBuilder.newJob(CleanupJob.class)
                .withIdentity("cleanupJob", "maintenance")
                .withDescription("Cleans up old processed documents")
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("cleanupTrigger", "maintenance")
                .withDescription("Fires every 5 minutes")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(5)
                        .repeatForever())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("CleanupJob scheduled: {}", trigger.getDescription());
    }

    /**
     * Schedules the ReindexJob to run every 10 minutes.
     * This job triggers a full reindex of all documents in the Lucene search service.
     */
    private void scheduleReindexJob(Scheduler scheduler) throws SchedulerException {
        logger.info("Scheduling ReindexJob (every 10 minutes)");

        JobDetail jobDetail = JobBuilder.newJob(ReindexJob.class)
                .withIdentity("reindexJob", "maintenance")
                .withDescription("Reindexes all documents in Lucene")
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("reindexTrigger", "maintenance")
                .withDescription("Fires every 10 minutes")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(10)
                        .repeatForever())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("ReindexJob scheduled: {}", trigger.getDescription());
    }

    /**
     * Schedules the HealthCheckJob to run every 1 minute.
     * This job pings all service health endpoints and logs their status.
     */
    private void scheduleHealthCheckJob(Scheduler scheduler) throws SchedulerException {
        logger.info("Scheduling HealthCheckJob (every 1 minute)");

        JobDetail jobDetail = JobBuilder.newJob(HealthCheckJob.class)
                .withIdentity("healthCheckJob", "monitoring")
                .withDescription("Pings all service health endpoints")
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("healthCheckTrigger", "monitoring")
                .withDescription("Fires every 1 minute")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1)
                        .repeatForever())
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("HealthCheckJob scheduled: {}", trigger.getDescription());
    }
}
