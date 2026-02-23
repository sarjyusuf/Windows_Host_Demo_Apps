package com.dragonracing.media.servlet;

import com.dragonracing.media.generator.DragonPortraitGenerator;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PortraitServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(PortraitServlet.class);
    private final DragonPortraitGenerator generator = new DragonPortraitGenerator();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Parse dragon ID from path: /dragons/{id}/portrait
        String requestURI = req.getRequestURI();
        String[] parts = requestURI.split("/");

        Long dragonId = null;
        for (int i = 0; i < parts.length; i++) {
            if ("dragons".equals(parts[i]) && i + 1 < parts.length) {
                try {
                    dragonId = Long.parseLong(parts[i + 1]);
                } catch (NumberFormatException e) {
                    // not a number
                }
                break;
            }
        }

        if (dragonId == null) {
            resp.setContentType("application/json");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\": \"Invalid dragon ID in path\"}");
            return;
        }

        // Get optional query params for breed/stats
        String breed = req.getParameter("breed");
        String speedStr = req.getParameter("speed");
        String firepowerStr = req.getParameter("firepower");
        String name = req.getParameter("name");

        if (breed == null) breed = getBreedFromId(dragonId);
        if (name == null) name = "Dragon #" + dragonId;

        int speed = parseIntOrDefault(speedStr, 10);
        int firepower = parseIntOrDefault(firepowerStr, 10);

        log.info("Generating portrait for dragon {} (breed: {}, name: {})", dragonId, breed, name);

        String svg = generator.generatePortrait(dragonId, name, breed, speed, firepower);

        resp.setContentType("image/svg+xml");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(svg);
    }

    private String getBreedFromId(long id) {
        // Default breed mapping for seeded dragons
        return switch ((int) id) {
            case 1 -> "Shadow Dragon";
            case 2 -> "Fire Drake";
            case 3 -> "Ice Wyrm";
            case 4 -> "Thunder Beast";
            case 5 -> "Void Walker";
            case 6 -> "Fire Drake";
            case 7 -> "Storm Serpent";
            case 8 -> "Shadow Dragon";
            default -> "Fire Drake";
        };
    }

    private int parseIntOrDefault(String str, int defaultVal) {
        if (str == null) return defaultVal;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
