package com.dragonracing.media;

import com.dragonracing.media.servlet.HealthServlet;
import com.dragonracing.media.servlet.ImageServlet;
import com.dragonracing.media.servlet.PortraitServlet;
import com.dragonracing.media.servlet.UploadServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class MediaServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(MediaServiceApplication.class);
    private static final String MEDIA_DIR = "C:\\dragon-racing\\data\\media";

    private static final String BANNER = """

            ============================================================
                     MEDIA SERVICE - Dragon Portraits & Gallery
            ============================================================

                        .--.
                       /    \\      .---.
                      |  ()  |    /     \\
                       \\    /    | IMAGE |
                        '--'     \\     /
                       /    \\     '---'
                      | DRAG |   .--.
                      | -ON  |  /    \\
                       \\    /  | SVG  |
                        '--'   \\    /
                       /    \\   '--'
                      |MEDIA |
                       \\    /
                        '--'
                     ~~ Dragon Media Hub ~~

                 Port: 9083  |  Storage: media/
                 API: /upload | /images | /dragons
            ============================================================
            """;

    public static void main(String[] args) throws Exception {
        System.out.println(BANNER);
        log.info("Starting Dragon Media Service...");

        // Ensure media directory exists
        File mediaDir = new File(MEDIA_DIR);
        if (!mediaDir.exists()) {
            mediaDir.mkdirs();
            log.info("Created media directory: {}", mediaDir.getAbsolutePath());
        }

        Server server = new Server(9083);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        // Register servlets
        context.addServlet(new ServletHolder(new UploadServlet(MEDIA_DIR)), "/upload");
        context.addServlet(new ServletHolder(new ImageServlet(MEDIA_DIR)), "/images/*");
        context.addServlet(new ServletHolder(new PortraitServlet()), "/dragons/*");
        context.addServlet(new ServletHolder(new HealthServlet(MEDIA_DIR)), "/health");

        server.setHandler(context);
        server.start();

        log.info("Dragon Media Service is ONLINE on port 9083!");
        log.info("Upload images at POST /upload");
        log.info("View images at GET /images/{filename}");
        log.info("Generate dragon portraits at GET /dragons/{dragonId}/portrait");

        server.join();
    }
}
