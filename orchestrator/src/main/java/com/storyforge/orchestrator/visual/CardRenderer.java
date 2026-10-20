package com.storyforge.orchestrator.visual;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public final class CardRenderer {

    private static final Color INK = new Color(245, 240, 230);
    private static final Color SHADOW = new Color(0, 0, 0, 140);

    private CardRenderer() {
    }

    public static void placeholder(Path out, int width, int height, long seed, String label, String body) {
        BufferedImage image = canvas(width, height, seed);
        Graphics2D g = graphics(image);
        int margin = width / 16;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, height / 14));
        g.setColor(INK);
        g.drawString(label.toUpperCase(Locale.ROOT), margin, margin + height / 14);
        g.setFont(new Font(Font.SERIF, Font.PLAIN, height / 26));
        drawWrapped(g, body, margin, margin + height / 6, width - 2 * margin, 7);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, height / 40));
        g.setColor(new Color(255, 255, 255, 160));
        g.drawString("PLACEHOLDER  seed " + seed, margin, height - margin / 2);
        g.dispose();
        write(image, out);
    }

    /** Text-free BiPath night backdrop (radial glow and snow), the last resort when no picture can be found. */
    public static void backdrop(Path out, int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(image);
        g.setPaint(new java.awt.RadialGradientPaint(width * 0.5f, height * 0.42f, width * 0.6f,
                new float[]{0f, 1f}, new Color[]{new Color(0x1c, 0x2c, 0x44), new Color(0x0b, 0x0f, 0x17)}));
        g.fillRect(0, 0, width, height);
        java.util.Random random = new java.util.Random(seed);
        for (int i = 0; i < 140; i++) {
            int size = 2 + random.nextInt(5);
            g.setColor(new Color(240, 240, 240, 60 + random.nextInt(150)));
            g.fillOval(random.nextInt(width), random.nextInt(height), size, size);
        }
        g.dispose();
        write(image, out);
    }

    public static void kineticText(Path out, int width, int height, long seed, String text) {
        BufferedImage image = canvas(width, height, seed);
        Graphics2D g = graphics(image);
        g.setColor(new Color(0, 0, 0, 90));
        g.fillRect(0, 0, width, height);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, height / 9);
        g.setFont(font);
        List<String> lines = wrap(g.getFontMetrics(), text, width * 3 / 4);
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        int y = (height - lineHeight * lines.size()) / 2 + metrics.getAscent();
        for (String line : lines) {
            int x = (width - metrics.stringWidth(line)) / 2;
            g.setColor(SHADOW);
            g.drawString(line, x + 4, y + 4);
            g.setColor(INK);
            g.drawString(line, x, y);
            y += lineHeight;
        }
        g.dispose();
        write(image, out);
    }

    public static void thumbnail(Path out, int width, int height, long seed, String text, Path background) {
        BufferedImage image = canvas(width, height, seed);
        Graphics2D g = graphics(image);
        if (background != null) {
            try {
                BufferedImage source = ImageIO.read(background.toFile());
                if (source != null) {
                    double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
                    int w = (int) Math.ceil(source.getWidth() * scale);
                    int h = (int) Math.ceil(source.getHeight() * scale);
                    g.drawImage(source, (width - w) / 2, (height - h) / 2, w, h, null);
                    g.setColor(new Color(0, 0, 0, 110));
                    g.fillRect(0, 0, width, height);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, height / 5));
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = wrap(metrics, text.toUpperCase(Locale.ROOT), width * 9 / 10);
        int y = height - metrics.getDescent() - (lines.size() - 1) * metrics.getHeight() - height / 12;
        for (String line : lines) {
            g.setColor(Color.BLACK);
            g.drawString(line, width / 20 + 6, y + 6);
            g.setColor(new Color(255, 214, 0));
            g.drawString(line, width / 20, y);
            y += metrics.getHeight();
        }
        g.dispose();
        write(image, out);
    }

    private static BufferedImage canvas(int width, int height, long seed) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        float hue = Math.floorMod(seed * 37, 360) / 360f;
        g.setPaint(new GradientPaint(0, 0, Color.getHSBColor(hue, 0.45f, 0.62f),
                width, height, Color.getHSBColor((hue + 0.08f) % 1f, 0.6f, 0.38f)));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    private static void drawWrapped(Graphics2D g, String text, int x, int y, int maxWidth, int maxLines) {
        FontMetrics metrics = g.getFontMetrics();
        List<String> lines = wrap(metrics, text == null ? "" : text, maxWidth);
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            g.drawString(lines.get(i), x, y + i * metrics.getHeight());
        }
    }

    private static List<String> wrap(FontMetrics metrics, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static void write(BufferedImage image, Path out) {
        try {
            if (!ImageIO.write(image, "png", out.toFile())) {
                throw new IOException("No PNG writer available");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
