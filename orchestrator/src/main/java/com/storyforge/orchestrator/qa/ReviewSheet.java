package com.storyforge.orchestrator.qa;

import com.storyforge.orchestrator.media.FfmpegService;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * The finished video at a glance: one frame from the middle of every shot, on numbered pages, with the shot, the
 * kind of picture and the words spoken under it. It is the render-then-review loop of motion-design work, without an
 * AI call: the owner sees the whole picture choice before publishing. Frames that are almost one flat colour or almost
 * black are listed for a QA warning.
 */
@Component
public class ReviewSheet {

    public static final String PREFIX = "review_";
    private static final int COLS = 5;
    private static final int ROWS = 6;
    private static final int W = 384;
    private static final int H = 216;
    private static final int TEXT_H = 72;
    /**
     * Spread of grey levels under which a frame shows nothing (a flat card, a fade). Measured on Fight Club: real dark
     * scenes spread 11-30, a flat frame about 0.
     */
    static final double FLAT_SPREAD = 6;
    /** Average grey level under which a frame is black on a phone (the darkest real shots of that film: 4-5). */
    static final double DARK_MEAN = 6;
    private static final Map<String, Color> COLOURS = Map.of(
            "footage", new Color(70, 200, 120), "animated_text", new Color(120, 170, 255),
            "host", new Color(255, 200, 80), "map", new Color(120, 170, 255),
            "ai_picture", new Color(255, 90, 90), "reused", new Color(255, 60, 160),
            "real_media", new Color(90, 220, 230), "backdrop", new Color(255, 0, 0));

    /** at: the time in the final video; kind: the type of picture; spoken: the words said during the shot. */
    public record Tile(String shotId, double at, double seconds, String kind, String spoken) {
    }

    public record Result(List<Path> pages, List<String> flatShots) {
    }

    private final FfmpegService ffmpeg;

    public ReviewSheet(FfmpegService ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /** Writes review_01.jpg, review_02.jpg... into {@code dir}. */
    public Result build(Path video, List<Tile> tiles, Path dir) {
        try {
            Files.createDirectories(dir);
            Path frame = Files.createTempFile("storyforge-review", ".png");
            List<Path> pages = new ArrayList<>();
            List<String> flat = new ArrayList<>();
            int perPage = COLS * ROWS;
            try {
                for (int first = 0; first < tiles.size(); first += perPage) {
                    BufferedImage page = new BufferedImage(COLS * W, ROWS * (H + TEXT_H), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = page.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(new Color(18, 18, 20));
                    g.fillRect(0, 0, page.getWidth(), page.getHeight());
                    for (int i = first; i < Math.min(tiles.size(), first + perPage); i++) {
                        Tile tile = tiles.get(i);
                        int x = (i - first) % COLS * W;
                        int y = (i - first) / COLS * (H + TEXT_H);
                        BufferedImage picture = frame(video, tile.at(), frame);
                        if (picture != null) {
                            g.drawImage(picture, x, y, W, H, null);
                            if (flat(picture)) {
                                flat.add(tile.shotId());
                            }
                        }
                        caption(g, tile, x, y + H);
                    }
                    g.dispose();
                    Path out = dir.resolve(String.format(Locale.ROOT, "%s%02d.jpg", PREFIX, pages.size() + 1));
                    ImageIO.write(page, "jpg", out.toFile());
                    pages.add(out);
                }
            } finally {
                Files.deleteIfExists(frame);
            }
            return new Result(pages, flat);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage frame(Path video, double at, Path out) throws IOException {
        try {
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", String.format(Locale.ROOT, "%.3f", at), "-i",
                    video.toString(), "-frames:v", "1", "-vf", "scale=" + W + ":" + H, out.toString()));
            return ImageIO.read(out.toFile());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void caption(Graphics2D g, Tile tile, int x, int y) {
        g.setColor(new Color(50, 50, 55));
        g.drawRect(x, y - H, W - 1, H + TEXT_H - 1);
        g.setColor(new Color(32, 32, 36));
        g.fillRect(x + 1, y, W - 2, 18);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(COLOURS.getOrDefault(tile.kind(), new Color(200, 200, 200)));
        g.drawString(String.format(Locale.ROOT, "%s  %d:%02d  %.1fs  %s", tile.shotId(), (int) tile.at() / 60,
                (int) tile.at() % 60, tile.seconds(), tile.kind()), x + 5, y + 14);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(225, 225, 225));
        List<String> lines = wrap(tile.spoken(), g.getFontMetrics(), W - 10);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            g.drawString(lines.get(i), x + 5, y + 33 + i * 16);
        }
    }

    static List<String> wrap(String text, FontMetrics metrics, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split("\\s+")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(next) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** True when the frame shows nothing: almost one flat colour, or almost black. */
    static boolean flat(BufferedImage image) {
        double sum = 0;
        double squares = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 4) {
                int rgb = image.getRGB(x, y);
                double grey = (((rgb >> 16) & 255) * 299 + ((rgb >> 8) & 255) * 587 + (rgb & 255) * 114) / 1000.0;
                sum += grey;
                squares += grey * grey;
                count++;
            }
        }
        double mean = sum / Math.max(1, count);
        double spread = Math.sqrt(Math.max(0, squares / Math.max(1, count) - mean * mean));
        return spread < FLAT_SPREAD || mean < DARK_MEAN;
    }
}
