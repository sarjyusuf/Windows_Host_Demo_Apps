package com.dragonracing.schedule.resource;

import com.dragonracing.schedule.model.Track;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/tracks")
@Produces(MediaType.APPLICATION_JSON)
public class TrackResource {

    private static final List<Track> TRACKS = List.of(
            new Track("Dragon's Peak Circuit",
                    "Mountain",
                    "Expert",
                    42,
                    "A treacherous mountain circuit with sharp switchbacks and thin air. Only the hardiest dragons survive."),
            new Track("Inferno Valley Sprint",
                    "Volcanic",
                    "Hard",
                    28,
                    "A scorching straight through active lava flows. Fire Drakes have a natural advantage here."),
            new Track("Frostfang Mountain Rally",
                    "Arctic",
                    "Hard",
                    35,
                    "A winding rally through frozen peaks and ice caves. Ice Wyrms feel right at home."),
            new Track("Skyreach Arena",
                    "Aerial",
                    "Medium",
                    20,
                    "An open-sky arena high above the clouds. Pure speed and agility determine the winner."),
            new Track("Deadlands Dash",
                    "Desert",
                    "Medium",
                    30,
                    "A brutal dash through the barren wastelands. Stamina is key to surviving the heat.")
    );

    @GET
    public Response listTracks() {
        return Response.ok(TRACKS).build();
    }
}
