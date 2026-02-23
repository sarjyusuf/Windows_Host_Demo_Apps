package com.dragonracing.sync.job;

import com.dragonracing.sync.config.ServiceEndpoints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
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
 * Dragon Aging Job - Runs every 5 minutes.
 * Simulates the passage of time in the dragon world by slightly adjusting dragon stats.
 * Calls the Dragon Stable API to train random dragons.
 */
public class DragonAgingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DragonAgingJob.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String[] FLAVOR_MESSAGES = {
            "The dragons grow restless in their stables...",
            "A strange wind blows through the racing grounds...",
            "The ancient dragon spirits stir beneath the mountains...",
            "Moonlight bathes the stables - the dragons dream of glory...",
            "The smell of sulfur fills the air as dragons practice their fire breath...",
            "Thunder rumbles in the distance - the Thunder Beasts awaken...",
            "Ice crystals form on the stable windows - the Wyrms are restless...",
            "Shadows dance along the walls as Shadow Dragons train in darkness...",
            "The Void Walkers phase in and out of reality...",
            "A dragon egg cracks somewhere in the breeding grounds..."
    };

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String flavor = FLAVOR_MESSAGES[ThreadLocalRandom.current().nextInt(FLAVOR_MESSAGES.length)];
        log.info("[DRAGON AGING] {}", flavor);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // Fetch all dragons
            HttpGet getRequest = new HttpGet(ServiceEndpoints.DRAGON_STABLE_DRAGONS);
            String responseBody = httpClient.execute(getRequest, response -> {
                int status = response.getCode();
                if (status != 200) {
                    log.warn("[DRAGON AGING] Dragon Stable returned HTTP {}", status);
                    return null;
                }
                return EntityUtils.toString(response.getEntity());
            });

            if (responseBody == null) {
                log.warn("[DRAGON AGING] Could not reach Dragon Stable. The dragons slumber undisturbed.");
                return;
            }

            JsonNode dragons = objectMapper.readTree(responseBody);
            if (!dragons.isArray() || dragons.isEmpty()) {
                log.info("[DRAGON AGING] No dragons found in the stable.");
                return;
            }

            // Pick 1-3 random dragons to "train"
            int dragonsToTrain = ThreadLocalRandom.current().nextInt(1, Math.min(4, dragons.size() + 1));
            log.info("[DRAGON AGING] {} dragon(s) feel the call to train today!", dragonsToTrain);

            for (int i = 0; i < dragonsToTrain; i++) {
                int idx = ThreadLocalRandom.current().nextInt(dragons.size());
                JsonNode dragon = dragons.get(idx);
                long dragonId = dragon.get("id").asLong();
                String dragonName = dragon.get("name").asText();

                // Call the train endpoint
                String trainUrl = ServiceEndpoints.DRAGON_STABLE_DRAGONS + "/" + dragonId + "/train";
                HttpPut trainRequest = new HttpPut(trainUrl);

                String trainResult = httpClient.execute(trainRequest, response -> {
                    if (response.getCode() == 200) {
                        return EntityUtils.toString(response.getEntity());
                    }
                    return null;
                });

                if (trainResult != null) {
                    JsonNode result = objectMapper.readTree(trainResult);
                    String stat = result.has("trainedStat") ? result.get("trainedStat").asText() : "unknown";
                    int increase = result.has("increase") ? result.get("increase").asInt() : 0;
                    log.info("[DRAGON AGING] {} trained {} (+{})! The power within grows stronger.",
                            dragonName, stat, increase);
                } else {
                    log.warn("[DRAGON AGING] Failed to train {}. The dragon resists!", dragonName);
                }
            }

            log.info("[DRAGON AGING] Training session complete. The dragons return to their rest.");

        } catch (Exception e) {
            log.error("[DRAGON AGING] The aging magic falters: {}", e.getMessage());
        }
    }
}
