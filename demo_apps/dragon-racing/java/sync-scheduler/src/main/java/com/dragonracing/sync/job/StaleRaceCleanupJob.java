package com.dragonracing.sync.job;

import com.dragonracing.sync.config.ServiceEndpoints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stale Race Cleanup Job - Runs every 10 minutes.
 * Checks for races that have been "InProgress" for too long and logs warnings.
 */
public class StaleRaceCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(StaleRaceCleanupJob.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final long STALE_THRESHOLD_MINUTES = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] FLAVOR_MESSAGES = {
            "The race marshals inspect the tracks for abandoned races...",
            "Dust settles on the forgotten racing grounds...",
            "The cleanup crew sweeps through the racing district...",
            "Officials check for races lost in the fog of war...",
            "The timekeeper reviews his hourglass collection...",
            "Ancient race records are being archived in the Great Library..."
    };

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String flavor = FLAVOR_MESSAGES[ThreadLocalRandom.current().nextInt(FLAVOR_MESSAGES.length)];
        log.info("[RACE CLEANUP] {}", flavor);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = new HttpGet(ServiceEndpoints.RACE_SCHEDULE_RACES);

            String responseBody = httpClient.execute(getRequest, response -> {
                int status = response.getCode();
                if (status == 200) {
                    return EntityUtils.toString(response.getEntity());
                }
                log.warn("[RACE CLEANUP] Race Schedule service returned HTTP {}", status);
                return null;
            });

            if (responseBody == null) {
                log.warn("[RACE CLEANUP] Could not reach Race Schedule service. The tracks remain unchecked.");
                return;
            }

            JsonNode races = objectMapper.readTree(responseBody);
            if (!races.isArray()) {
                log.warn("[RACE CLEANUP] Unexpected response format from Race Schedule.");
                return;
            }

            int staleCount = 0;
            int totalRaces = races.size();
            int inProgressCount = 0;

            LocalDateTime now = LocalDateTime.now();

            for (JsonNode race : races) {
                String status = race.has("status") ? race.get("status").asText() : "";

                if ("InProgress".equals(status)) {
                    inProgressCount++;

                    String scheduledTimeStr = race.has("scheduledTime") ?
                            race.get("scheduledTime").asText() : null;

                    if (scheduledTimeStr != null) {
                        try {
                            LocalDateTime scheduledTime = LocalDateTime.parse(scheduledTimeStr, DT_FMT);
                            long minutesSinceStart = ChronoUnit.MINUTES.between(scheduledTime, now);

                            if (minutesSinceStart > STALE_THRESHOLD_MINUTES) {
                                staleCount++;
                                long raceId = race.get("id").asLong();
                                String trackName = race.has("trackName") ?
                                        race.get("trackName").asText() : "Unknown Track";

                                log.warn("[RACE CLEANUP] STALE RACE DETECTED! Race #{} at '{}' " +
                                                "has been InProgress for {} minutes (threshold: {} min).",
                                        raceId, trackName, minutesSinceStart, STALE_THRESHOLD_MINUTES);
                                log.warn("[RACE CLEANUP] The dragons at '{}' may have gotten lost! " +
                                        "Consider completing or cancelling this race.", trackName);
                            }
                        } catch (Exception e) {
                            log.debug("[RACE CLEANUP] Could not parse scheduled time: {}", scheduledTimeStr);
                        }
                    }
                }
            }

            if (staleCount > 0) {
                log.warn("[RACE CLEANUP] Found {} stale race(s) out of {} in-progress. " +
                        "The race marshals are concerned!", staleCount, inProgressCount);
            } else if (inProgressCount > 0) {
                log.info("[RACE CLEANUP] {} race(s) in progress, none stale. All races running smoothly!",
                        inProgressCount);
            } else {
                log.info("[RACE CLEANUP] No races currently in progress. Total races: {}. " +
                        "The tracks are quiet... for now.", totalRaces);
            }

        } catch (Exception e) {
            log.error("[RACE CLEANUP] The cleanup crew encountered trouble: {}", e.getMessage());
        }
    }
}
