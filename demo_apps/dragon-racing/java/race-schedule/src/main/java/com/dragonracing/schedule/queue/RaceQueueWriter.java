package com.dragonracing.schedule.queue;

import com.dragonracing.schedule.model.Race;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class RaceQueueWriter {

    private static final Logger log = LoggerFactory.getLogger(RaceQueueWriter.class);
    private final String queueDir;
    private final ObjectMapper objectMapper;

    public RaceQueueWriter(String queueDir) {
        this.queueDir = queueDir;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Ensure queue directory exists
        File dir = new File(queueDir);
        if (!dir.exists()) {
            dir.mkdirs();
            log.info("Created queue directory: {}", dir.getAbsolutePath());
        }
    }

    public void writeRaceToQueue(Race race) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("race_%d_%s.json", race.getId(), timestamp);
        File queueFile = new File(queueDir, filename);

        try {
            Map<String, Object> queueMessage = new LinkedHashMap<>();
            queueMessage.put("raceId", race.getId());
            queueMessage.put("trackName", race.getTrackName());
            queueMessage.put("scheduledTime", race.getScheduledTime() != null ?
                    race.getScheduledTime().toString() : null);
            queueMessage.put("status", race.getStatus().name());
            queueMessage.put("entryCount", race.getEntryCount());
            queueMessage.put("queuedAt", LocalDateTime.now().toString());

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(queueFile, queueMessage);
            log.info("Race {} queued to file: {}", race.getId(), queueFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write race {} to queue", race.getId(), e);
        }
    }
}
