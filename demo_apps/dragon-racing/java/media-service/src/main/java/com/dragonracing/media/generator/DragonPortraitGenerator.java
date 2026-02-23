package com.dragonracing.media.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Procedurally generates SVG dragon portraits based on breed and stats.
 * Each breed gets unique colors and the dragon silhouette varies with stats.
 */
public class DragonPortraitGenerator {

    private static final Logger log = LoggerFactory.getLogger(DragonPortraitGenerator.class);

    public String generatePortrait(long dragonId, String name, String breed, int speed, int firepower) {
        BreedColors colors = getBreedColors(breed);
        // Use dragonId as seed for procedural variation
        long seed = dragonId * 31 + breed.hashCode();

        int wingSpan = 60 + (speed * 2);       // faster = bigger wings
        int flameSize = 10 + (firepower * 3);   // more firepower = bigger flames
        int bodySize = 80 + ((int)(seed % 20)); // slight variation

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 400 400\" width=\"400\" height=\"400\">\n");
        svg.append("  <defs>\n");

        // Background gradient
        svg.append("    <radialGradient id=\"bg\" cx=\"50%\" cy=\"50%\" r=\"70%\">\n");
        svg.append(String.format("      <stop offset=\"0%%\" stop-color=\"%s\" stop-opacity=\"0.3\"/>\n", colors.accent));
        svg.append("      <stop offset=\"100%\" stop-color=\"#1a1a2e\"/>\n");
        svg.append("    </radialGradient>\n");

        // Body gradient
        svg.append(String.format("    <linearGradient id=\"bodyGrad\" x1=\"0%%\" y1=\"0%%\" x2=\"100%%\" y2=\"100%%\">\n"));
        svg.append(String.format("      <stop offset=\"0%%\" stop-color=\"%s\"/>\n", colors.primary));
        svg.append(String.format("      <stop offset=\"100%%\" stop-color=\"%s\"/>\n", colors.secondary));
        svg.append("    </linearGradient>\n");

        // Glow filter
        svg.append("    <filter id=\"glow\">\n");
        svg.append("      <feGaussianBlur stdDeviation=\"3\" result=\"coloredBlur\"/>\n");
        svg.append("      <feMerge>\n");
        svg.append("        <feMergeNode in=\"coloredBlur\"/>\n");
        svg.append("        <feMergeNode in=\"SourceGraphic\"/>\n");
        svg.append("      </feMerge>\n");
        svg.append("    </filter>\n");
        svg.append("  </defs>\n\n");

        // Background
        svg.append("  <rect width=\"400\" height=\"400\" fill=\"url(#bg)\"/>\n\n");

        // Stars/particles in background
        for (int i = 0; i < 15; i++) {
            int sx = (int)((seed * (i + 7) * 17) % 380 + 10);
            int sy = (int)((seed * (i + 3) * 23) % 380 + 10);
            int sr = 1 + (i % 3);
            svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" opacity=\"%.1f\"/>\n",
                    sx, sy, sr, colors.accent, 0.3 + (i % 5) * 0.1));
        }

        // -- Dragon Body --
        int cx = 200;
        int cy = 200;

        // Tail
        svg.append(String.format("  <path d=\"M %d %d Q %d %d %d %d Q %d %d %d %d\" " +
                        "fill=\"none\" stroke=\"url(#bodyGrad)\" stroke-width=\"8\" stroke-linecap=\"round\"/>\n",
                cx - 20, cy + 30, cx - 60, cy + 60, cx - 80, cy + 90,
                cx - 100, cy + 120, cx - 70, cy + 140));

        // Body (ellipse)
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"%d\" ry=\"%d\" fill=\"url(#bodyGrad)\" filter=\"url(#glow)\"/>\n",
                cx, cy, bodySize / 2, bodySize / 3));

        // Belly (lighter)
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"%d\" ry=\"%d\" fill=\"%s\" opacity=\"0.4\"/>\n",
                cx, cy + 5, bodySize / 3, bodySize / 5, colors.accent));

        // Left Wing
        int wingY = cy - 20;
        svg.append(String.format("  <path d=\"M %d %d L %d %d Q %d %d %d %d Q %d %d %d %d Z\" " +
                        "fill=\"%s\" opacity=\"0.7\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx - 15, wingY,
                cx - wingSpan, wingY - 60,
                cx - wingSpan + 20, wingY - 80,
                cx - wingSpan + 40, wingY - 40,
                cx - wingSpan + 50, wingY - 20,
                cx - 10, wingY + 10,
                colors.secondary, colors.primary));

        // Wing membrane lines (left)
        for (int i = 1; i <= 3; i++) {
            int lx = cx - 15 - (wingSpan - 15) * i / 4;
            int ly = wingY - 60 * i / 4;
            svg.append(String.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" " +
                    "stroke=\"%s\" stroke-width=\"1\" opacity=\"0.5\"/>\n",
                    cx - 15, wingY, lx, ly, colors.primary));
        }

        // Right Wing
        svg.append(String.format("  <path d=\"M %d %d L %d %d Q %d %d %d %d Q %d %d %d %d Z\" " +
                        "fill=\"%s\" opacity=\"0.7\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx + 15, wingY,
                cx + wingSpan, wingY - 60,
                cx + wingSpan - 20, wingY - 80,
                cx + wingSpan - 40, wingY - 40,
                cx + wingSpan - 50, wingY - 20,
                cx + 10, wingY + 10,
                colors.secondary, colors.primary));

        // Wing membrane lines (right)
        for (int i = 1; i <= 3; i++) {
            int rx = cx + 15 + (wingSpan - 15) * i / 4;
            int ry = wingY - 60 * i / 4;
            svg.append(String.format("  <line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" " +
                            "stroke=\"%s\" stroke-width=\"1\" opacity=\"0.5\"/>\n",
                    cx + 15, wingY, rx, ry, colors.primary));
        }

        // Neck
        svg.append(String.format("  <path d=\"M %d %d Q %d %d %d %d\" " +
                        "fill=\"url(#bodyGrad)\" stroke=\"url(#bodyGrad)\" stroke-width=\"20\"/>\n",
                cx - 5, cy - 20, cx + 5, cy - 50, cx + 10, cy - 70));

        // Head
        int headY = cy - 85;
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"22\" ry=\"18\" fill=\"url(#bodyGrad)\" filter=\"url(#glow)\"/>\n",
                cx + 10, headY));

        // Snout
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"12\" ry=\"8\" fill=\"%s\"/>\n",
                cx + 28, headY + 2, colors.primary));

        // Eye
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"5\" ry=\"4\" fill=\"%s\" filter=\"url(#glow)\"/>\n",
                cx + 15, headY - 5, colors.eye));
        svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"2\" ry=\"3\" fill=\"#000\"/>\n",
                cx + 16, headY - 5));

        // Horns
        svg.append(String.format("  <path d=\"M %d %d L %d %d L %d %d Z\" fill=\"%s\"/>\n",
                cx + 2, headY - 12, cx - 5, headY - 35, cx + 8, headY - 14, colors.accent));
        svg.append(String.format("  <path d=\"M %d %d L %d %d L %d %d Z\" fill=\"%s\"/>\n",
                cx + 15, headY - 15, cx + 12, headY - 38, cx + 20, headY - 16, colors.accent));

        // Nostril smoke/fire
        if (firepower > 8) {
            svg.append(String.format("  <ellipse cx=\"%d\" cy=\"%d\" rx=\"%d\" ry=\"%d\" " +
                            "fill=\"%s\" opacity=\"0.6\" filter=\"url(#glow)\">\n",
                    cx + 38, headY, flameSize / 3, flameSize / 4, colors.flame));
            svg.append("    <animate attributeName=\"opacity\" values=\"0.3;0.7;0.3\" dur=\"1.5s\" repeatCount=\"indefinite\"/>\n");
            svg.append("  </ellipse>\n");

            // Flame particles
            for (int i = 0; i < Math.min(firepower / 3, 5); i++) {
                int fx = cx + 40 + (i * 8);
                int fy = headY - 3 + ((int)(seed * (i+1)) % 10 - 5);
                int fr = 2 + (i % 3);
                svg.append(String.format("  <circle cx=\"%d\" cy=\"%d\" r=\"%d\" fill=\"%s\" opacity=\"0.5\">\n",
                        fx, fy, fr, colors.flame));
                svg.append(String.format("    <animate attributeName=\"cx\" values=\"%d;%d;%d\" dur=\"%.1fs\" repeatCount=\"indefinite\"/>\n",
                        fx, fx + 15, fx, 0.8 + i * 0.3));
                svg.append(String.format("    <animate attributeName=\"opacity\" values=\"0.5;0.1;0.5\" dur=\"%.1fs\" repeatCount=\"indefinite\"/>\n",
                        0.8 + i * 0.3));
                svg.append("  </circle>\n");
            }
        }

        // Legs
        int legY = cy + bodySize / 3 - 5;
        // Front left
        svg.append(String.format("  <path d=\"M %d %d L %d %d L %d %d\" " +
                        "fill=\"none\" stroke=\"url(#bodyGrad)\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n",
                cx - 20, legY - 5, cx - 25, legY + 30, cx - 30, legY + 50));
        // Front right
        svg.append(String.format("  <path d=\"M %d %d L %d %d L %d %d\" " +
                        "fill=\"none\" stroke=\"url(#bodyGrad)\" stroke-width=\"10\" stroke-linecap=\"round\"/>\n",
                cx + 20, legY - 5, cx + 25, legY + 30, cx + 30, legY + 50));
        // Claws
        svg.append(String.format("  <path d=\"M %d %d l -5 5 l 5 -2 l 5 5\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx - 30, legY + 50, colors.accent));
        svg.append(String.format("  <path d=\"M %d %d l -5 5 l 5 -2 l 5 5\" fill=\"none\" stroke=\"%s\" stroke-width=\"2\"/>\n",
                cx + 30, legY + 50, colors.accent));

        // Spines along back
        for (int i = 0; i < 5; i++) {
            int spineX = cx - 30 + i * 12;
            int spineY = cy - 25 - (int)(Math.sin(i * 0.8) * 5);
            svg.append(String.format("  <polygon points=\"%d,%d %d,%d %d,%d\" fill=\"%s\" opacity=\"0.8\"/>\n",
                    spineX - 3, spineY, spineX, spineY - 12, spineX + 3, spineY, colors.accent));
        }

        // Name plate at bottom
        svg.append("  <rect x=\"80\" y=\"350\" width=\"240\" height=\"35\" rx=\"8\" fill=\"#000\" opacity=\"0.6\"/>\n");
        svg.append(String.format("  <text x=\"200\" y=\"372\" font-family=\"Georgia, serif\" font-size=\"16\" " +
                        "fill=\"%s\" text-anchor=\"middle\" font-weight=\"bold\">%s</text>\n",
                colors.accent, escapeXml(name)));

        // Breed label
        svg.append(String.format("  <text x=\"200\" y=\"392\" font-family=\"Georgia, serif\" font-size=\"10\" " +
                        "fill=\"%s\" text-anchor=\"middle\" opacity=\"0.7\">%s</text>\n",
                colors.accent, escapeXml(breed)));

        svg.append("</svg>\n");

        return svg.toString();
    }

    private BreedColors getBreedColors(String breed) {
        return switch (breed) {
            case "Fire Drake" -> new BreedColors("#ff4400", "#cc2200", "#ff6600", "#ffaa00", "#ff0000");
            case "Ice Wyrm" -> new BreedColors("#4488ff", "#2255cc", "#66bbff", "#aaddff", "#00ccff");
            case "Storm Serpent" -> new BreedColors("#9944ff", "#6622cc", "#bb66ff", "#ddaaff", "#cc44ff");
            case "Shadow Dragon" -> new BreedColors("#333333", "#111111", "#555555", "#888888", "#ff3333");
            case "Thunder Beast" -> new BreedColors("#ffcc00", "#cc9900", "#ffdd33", "#ffee66", "#ffff00");
            case "Void Walker" -> new BreedColors("#660099", "#330066", "#8800cc", "#aa44ff", "#ff00ff");
            default -> new BreedColors("#888888", "#555555", "#aaaaaa", "#cccccc", "#ff6600");
        };
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private record BreedColors(String primary, String secondary, String accent, String eye, String flame) {}
}
