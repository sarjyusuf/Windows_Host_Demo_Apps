package com.dragonracing.schedule.resource;

import com.dragonracing.schedule.db.RaceDatabase;
import com.dragonracing.schedule.model.CompleteRequest;
import com.dragonracing.schedule.model.EnterRequest;
import com.dragonracing.schedule.model.Race;
import com.dragonracing.schedule.model.RaceEntry;
import com.dragonracing.schedule.queue.RaceQueueWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Path("/api/races")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RaceResource {

    private static final Logger log = LoggerFactory.getLogger(RaceResource.class);
    private final RaceDatabase raceDb;
    private final RaceQueueWriter queueWriter;
    private final ObjectMapper objectMapper;

    public RaceResource(RaceDatabase raceDb, RaceQueueWriter queueWriter) {
        this.raceDb = raceDb;
        this.queueWriter = queueWriter;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @GET
    public Response listRaces() {
        log.info("Listing all races...");
        List<Race> races = raceDb.findAllRaces();
        return Response.ok(races).build();
    }

    @GET
    @Path("/{id}")
    public Response getRace(@PathParam("id") long id) {
        Optional<Race> race = raceDb.findRaceById(id);
        if (race.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Race not found", "raceId", id))
                    .build();
        }
        return Response.ok(race.get()).build();
    }

    @POST
    public Response createRace(Map<String, Object> body) {
        String trackName = (String) body.get("trackName");
        if (trackName == null || trackName.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "trackName is required"))
                    .build();
        }

        LocalDateTime scheduledTime = null;
        if (body.containsKey("scheduledTime") && body.get("scheduledTime") != null) {
            scheduledTime = LocalDateTime.parse((String) body.get("scheduledTime"));
        } else {
            scheduledTime = LocalDateTime.now().plusHours(1);
        }

        Race race = raceDb.createRace(trackName, scheduledTime);
        if (race == null) {
            return Response.serverError().entity(Map.of("error", "Failed to create race")).build();
        }

        log.info("New race created at {}! ID: {}", trackName, race.getId());
        return Response.status(Response.Status.CREATED).entity(race).build();
    }

    @POST
    @Path("/{id}/enter")
    public Response enterRace(@PathParam("id") long id, EnterRequest request) {
        Optional<Race> raceOpt = raceDb.findRaceById(id);
        if (raceOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Race not found"))
                    .build();
        }

        Race race = raceOpt.get();
        if (race.getStatus() != Race.Status.Scheduled) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Can only enter dragons in Scheduled races"))
                    .build();
        }

        if (request.getDragonId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "dragonId is required"))
                    .build();
        }

        // Check if dragon is already entered
        boolean alreadyEntered = race.getEntries().stream()
                .anyMatch(e -> e.getDragonId().equals(request.getDragonId()));
        if (alreadyEntered) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "Dragon is already entered in this race"))
                    .build();
        }

        String dragonName = request.getDragonName() != null ? request.getDragonName() : "Unknown Dragon";
        RaceEntry entry = raceDb.addEntry(id, request.getDragonId(), dragonName);

        log.info("{} has entered race {} at {}!", dragonName, id, race.getTrackName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entry", entry);
        result.put("message", String.format("%s charges into the arena at %s!", dragonName, race.getTrackName()));

        return Response.ok(result).build();
    }

    @POST
    @Path("/{id}/start")
    public Response startRace(@PathParam("id") long id) {
        Optional<Race> raceOpt = raceDb.findRaceById(id);
        if (raceOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Race not found"))
                    .build();
        }

        Race race = raceOpt.get();
        if (race.getStatus() != Race.Status.Scheduled) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Race must be in Scheduled status to start"))
                    .build();
        }

        if (race.getEntries().size() < 2) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "At least 2 dragons must be entered to start a race"))
                    .build();
        }

        raceDb.updateRaceStatus(id, Race.Status.InProgress);
        race.setStatus(Race.Status.InProgress);

        // Write to queue
        queueWriter.writeRaceToQueue(race);

        log.info("RACE STARTED at {}! {} dragons competing!", race.getTrackName(), race.getEntryCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("race", raceDb.findRaceById(id).orElse(race));
        result.put("message", String.format("The gates are OPEN at %s! %d dragons burst from the starting line!",
                race.getTrackName(), race.getEntryCount()));

        return Response.ok(result).build();
    }

    @POST
    @Path("/{id}/complete")
    public Response completeRace(@PathParam("id") long id, CompleteRequest request) {
        Optional<Race> raceOpt = raceDb.findRaceById(id);
        if (raceOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Race not found"))
                    .build();
        }

        Race race = raceOpt.get();
        if (race.getStatus() != Race.Status.InProgress) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Race must be InProgress to complete"))
                    .build();
        }

        try {
            String resultsJson = objectMapper.writeValueAsString(request.getResults());
            raceDb.updateRaceResults(id, resultsJson);
        } catch (Exception e) {
            log.error("Failed to serialize results", e);
            raceDb.updateRaceResults(id, "[]");
        }

        log.info("Race {} at {} is COMPLETE!", id, race.getTrackName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("race", raceDb.findRaceById(id).orElse(race));
        result.put("message", String.format("The race at %s is COMPLETE! The crowd goes wild!", race.getTrackName()));

        return Response.ok(result).build();
    }
}
