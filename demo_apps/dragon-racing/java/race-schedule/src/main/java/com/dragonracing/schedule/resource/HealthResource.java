package com.dragonracing.schedule.resource;

import com.dragonracing.schedule.db.RaceDatabase;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    private final RaceDatabase raceDb;

    public HealthResource(RaceDatabase raceDb) {
        this.raceDb = raceDb;
    }

    @GET
    public Response health() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "race-schedule");
        health.put("raceCount", raceDb.raceCount());
        health.put("timestamp", System.currentTimeMillis());
        return Response.ok(health).build();
    }
}
