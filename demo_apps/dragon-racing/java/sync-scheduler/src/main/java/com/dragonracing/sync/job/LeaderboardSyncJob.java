package com.dragonracing.sync.job;

import com.dragonracing.sync.config.ServiceEndpoints;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Leaderboard Sync Job - Runs every 2 minutes.
 * Triggers the leaderboard service to resync rankings from Dragon Stable.
 */
public class LeaderboardSyncJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardSyncJob.class);

    private static final String[] FLAVOR_MESSAGES = {
            "The scribes update the Hall of Champions...",
            "New rankings are being chiseled into the leaderboard stone...",
            "The crowd gathers to see the latest standings...",
            "Bards sing of the current top dragon's victories...",
            "The betting odds shift as new data flows in...",
            "Merchants adjust their dragon memorabilia prices based on rankings...",
            "Young dragonlings study the leaderboard, dreaming of glory...",
            "The season standings are being recalculated by the Council of Elders..."
    };

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String flavor = FLAVOR_MESSAGES[ThreadLocalRandom.current().nextInt(FLAVOR_MESSAGES.length)];
        log.info("[LEADERBOARD SYNC] {}", flavor);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost syncRequest = new HttpPost(ServiceEndpoints.LEADERBOARD_SYNC);

            String responseBody = httpClient.execute(syncRequest, response -> {
                int status = response.getCode();
                if (status == 200) {
                    return EntityUtils.toString(response.getEntity());
                }
                log.warn("[LEADERBOARD SYNC] Leaderboard service returned HTTP {}", status);
                return null;
            });

            if (responseBody != null) {
                log.info("[LEADERBOARD SYNC] Rankings synchronized successfully! The Hall of Champions is updated.");
                log.debug("[LEADERBOARD SYNC] Response: {}", responseBody);
            } else {
                log.warn("[LEADERBOARD SYNC] Could not reach Leaderboard service. The Hall of Champions remains unchanged.");
            }

        } catch (Exception e) {
            log.error("[LEADERBOARD SYNC] The scribes' quills have run dry: {}", e.getMessage());
        }
    }
}
