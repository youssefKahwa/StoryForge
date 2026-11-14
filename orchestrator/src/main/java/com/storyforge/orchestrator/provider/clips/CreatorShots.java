package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.clips.FootageIndex.Look;
import com.storyforge.orchestrator.provider.clips.FootageIndex.Segment;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * What the owner's main inspiration video shows while it tells the story. Our script adapts it in the same order, so
 * the moment the creator showed for an idea is a strong pick for our shot on the same idea.
 * <ul>
 *   <li>Exact: each cut of the main video is compared with the footage library, frame against frame (normal and
 *       mirrored). Only near-identical frames count: with the owner's copy of the film in the library this finds
 *       nearly everything; with trailers only, the few moments they share.</li>
 *   <li>Described: Claude looks at the whole main video once (one tile every ~6 s) and says in a few words what is
 *       on screen and whether it is the work's footage; cached per video, so a new library costs nothing.</li>
 * </ul>
 * The main video's picture is only read, never shown; its file is deleted once its frames and tiles are taken.
 */
final class CreatorShots {

    /** One stretch of the main video: a library moment it showed (exact), or Claude's words for what was there. */
    record Part(double start, double end, Integer moment, String shown, boolean film) {
    }

    record Timeline(int version, String library, List<Part> parts) {
        /** Share of the main video's footage time (of the work) that the library holds exactly. */
        double coverage() {
            double exact = parts.stream().filter(p -> p.moment() != null).mapToDouble(p -> p.end() - p.start()).sum();
            double film = parts.stream().filter(p -> p.moment() == null && p.film())
                    .mapToDouble(p -> p.end() - p.start()).sum();
            return film <= 0 ? (exact > 0 ? 1 : 0) : Math.min(1, exact / film);
        }
    }

    public record Described(Integer tile, String shown, Boolean film) {
    }

    public record Descriptions(List<Described> tiles) {
    }

    record Window(double start, double end) {
    }

    record Shots(int version, List<Window> windows, List<Described> tiles) {
    }

    record MainVideo(String url, VideoTranscript transcript) {
        String id() {
            return transcript.videoId() != null && !transcript.videoId().isBlank() ? transcript.videoId()
                    : Integer.toHexString(url.hashCode());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(CreatorShots.class);
    private static final int VERSION = 1;
    static final double FINE_MATCH = 0.9;
    private static final int CANDIDATES = 8;
    static final double MIN_PIECE = 0.5;
    static final double WINDOW = 6;
    static final double LINE_WINDOW = 10;
    private static final int MAX_SAID_WORDS = 25;
    private static final int FPS = 4;
    private static final int TILE_W = 320;
    private static final int TILE_H = 180;
    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final int SHEETS_PER_CALL = 6;

    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final MediaSvcClient media;
    private final FootageIndex index;
    private final LlmRouter llm;
    private final Path videos;
    private final Path claudeWork;

    CreatorShots(FfmpegService ffmpeg, JsonMapper json, MediaSvcClient media, FootageIndex index, LlmRouter llm,
                 Path videos, Path claudeWork) {
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.media = media;
        this.index = index;
        this.llm = llm;
        this.videos = videos;
        this.claudeWork = claudeWork;
    }

    /** The creator's timeline against this library; cached in the library folder until the library changes. */
    Timeline timeline(StageContext context, String work, MainVideo main, List<Segment> library, Path libraryDir)
            throws IOException {
        String key = library.size() + ":" + Integer.toHexString(
                library.stream().map(Segment::key).collect(Collectors.joining(",")).hashCode());
        Path cache = libraryDir.resolve("creator_" + FilmClipFinder.slug(main.id()) + ".json");
        if (Files.isRegularFile(cache)) {
            try {
                Timeline known = json.readValue(cache.toFile(), Timeline.class);
                if (known.version() == VERSION && key.equals(known.library())) {
                    return known;
                }
            } catch (RuntimeException e) {
                log.debug("Creator timeline {} unreadable, made again", cache);
            }
        }
        Files.createDirectories(videos);
        MediaSvcClient.Trailer source = new MediaSvcClient.Trailer(videos.resolve(FilmClipFinder.slug(main.id())
                + ".mp4"), main.id(), main.transcript().title(), "", main.url(), 0);
        Path frameFile = videos.resolve(FilmClipFinder.slug(main.id()) + ".frames");
        Path cutFile = videos.resolve(FilmClipFinder.slug(main.id()) + ".cuts.json");
        Path shotsFile = videos.resolve(FilmClipFinder.slug(main.id()) + ".shots.json");
        boolean needVideo = !Files.isRegularFile(frameFile) || !Files.isRegularFile(cutFile)
                || !Files.isRegularFile(shotsFile);
        if (needVideo && !Files.isRegularFile(source.file())) {
            MediaSvcClient.Trailer downloaded = media.video(main.url(), videos, 480);
            Files.move(downloaded.file(), source.file(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            byte[] raw = index.frames(source, videos, FPS);
            float[][] small = FootageIndex.pooled(raw);
            List<Double> cuts = FootageIndex.withHiddenCuts(index.cuts(source, videos), small, FPS);
            List<double[]> pieces = pieces(cuts, small.length / (double) FPS);
            List<Part> parts = new ArrayList<>(exact(pieces, raw, index.looks(library, libraryDir)));
            parts.addAll(described(context, work, source, pieces, shotsFile));
            parts.sort((a, b) -> Double.compare(a.start(), b.start()));
            Timeline timeline = new Timeline(VERSION, key, parts);
            json.writerWithDefaultPrettyPrinter().writeValue(cache.toFile(), timeline);
            log.info("Main video {}: {} exact moment(s) from the library, {} described part(s), library holds {}% of "
                    + "its footage", main.id(), parts.stream().filter(p -> p.moment() != null).count(),
                    parts.stream().filter(p -> p.moment() == null).count(), Math.round(timeline.coverage() * 100));
            return timeline;
        } finally {
            if (Files.isRegularFile(frameFile) && Files.isRegularFile(cutFile) && Files.isRegularFile(shotsFile)) {
                Files.deleteIfExists(source.file());
            }
        }
    }

    /** The cuts of the main video as pieces of at least MIN_PIECE seconds. */
    static List<double[]> pieces(List<Double> cuts, double duration) {
        List<Double> all = new ArrayList<>(cuts);
        if (all.isEmpty() || all.get(all.size() - 1) < duration - 0.01) {
            all.add(duration);
        }
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < all.size(); i++) {
            if (all.get(i + 1) - all.get(i) >= MIN_PIECE) {
                out.add(new double[] {all.get(i), Math.min(all.get(i + 1), duration)});
            }
        }
        return out;
    }

    /** Pieces whose middle frame is (almost) a frame of a library segment, normal or mirrored. */
    static List<Part> exact(List<double[]> pieces, byte[] raw, List<Look> looks) {
        List<Part> out = new ArrayList<>();
        int size = FootageIndex.frameSize();
        int count = raw.length / size;
        for (double[] piece : pieces) {
            int frame = Math.min(count - 1, (int) ((piece[0] + piece[1]) / 2 * FPS));
            if (frame < 0) {
                continue;
            }
            Segment best = null;
            double bestScore = 0;
            for (byte[] candidate : List.of(Arrays.copyOfRange(raw, frame * size, (frame + 1) * size),
                    FootageIndex.mirrored(raw, frame))) {
                Match match = match(candidate, looks);
                if (match.score() > bestScore) {
                    bestScore = match.score();
                    best = match.segment();
                }
            }
            if (best != null && bestScore >= FINE_MATCH) {
                out.add(new Part(piece[0], piece[1], best.moment(), null, true));
            }
        }
        return out;
    }

    record Match(double score, Segment segment) {
    }

    /** The quick look narrows the library to a few candidates, the fine look decides. */
    static Match match(byte[] frame, List<Look> looks) {
        float[] coarse = FootageIndex.unit(FootageIndex.pooled(frame)[0]);
        float[] fine = FootageIndex.fine(frame, 0);
        boolean flat = true;
        for (float v : coarse) {
            if (v != 0) {
                flat = false;
                break;
            }
        }
        if (flat || looks.isEmpty()) {
            return new Match(0, null);
        }
        double[] quick = new double[looks.size()];
        for (int i = 0; i < looks.size(); i++) {
            quick[i] = best(coarse, looks.get(i).coarse());
        }
        Integer[] order = new Integer[looks.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(quick[b], quick[a]));
        Match best = new Match(0, null);
        for (int k = 0; k < Math.min(CANDIDATES, order.length); k++) {
            Look look = looks.get(order[k]);
            double score = best(fine, look.fine());
            if (score > best.score()) {
                best = new Match(score, look.segment());
            }
        }
        return best;
    }

    private static double best(float[] query, float[][] frames) {
        double best = -1;
        for (float[] frame : frames) {
            double dot = 0;
            for (int i = 0; i < query.length; i++) {
                dot += query[i] * frame[i];
            }
            best = Math.max(best, dot);
        }
        return best;
    }

    /** About WINDOW-second stretches of the main video, cut on its own cuts, for Claude to describe. */
    static List<Window> windows(List<double[]> pieces) {
        List<Window> out = new ArrayList<>();
        double start = -1;
        double end = -1;
        for (double[] piece : pieces) {
            if (start < 0) {
                start = piece[0];
            }
            end = piece[1];
            while (end - start >= 2 * WINDOW) {
                out.add(new Window(start, start + WINDOW));
                start += WINDOW;
            }
            if (end - start >= WINDOW) {
                out.add(new Window(start, end));
                start = -1;
            }
        }
        if (start >= 0 && end - start >= 1) {
            out.add(new Window(start, end));
        }
        return out;
    }

    /** Claude's words for every window, made once per main video (the tiles are kept, the video is not). */
    private List<Part> described(StageContext context, String work, MediaSvcClient.Trailer source,
                                 List<double[]> pieces, Path shotsFile) throws IOException {
        List<Window> windows = windows(pieces);
        Shots shots = Files.isRegularFile(shotsFile) ? json.readValue(shotsFile.toFile(), Shots.class) : null;
        if (shots == null || shots.version() != VERSION || !windows.equals(shots.windows())) {
            shots = new Shots(VERSION, windows, describe(context, work, source, windows));
            if (!shots.tiles().isEmpty()) {
                json.writerWithDefaultPrettyPrinter().writeValue(shotsFile.toFile(), shots);
            }
        }
        List<Part> out = new ArrayList<>();
        for (Described d : shots.tiles()) {
            if (d.tile() != null && d.tile() >= 1 && d.tile() <= windows.size() && d.shown() != null
                    && !d.shown().isBlank()) {
                Window w = windows.get(d.tile() - 1);
                out.add(new Part(w.start(), w.end(), null, d.shown().strip(), Boolean.TRUE.equals(d.film())));
            }
        }
        return out;
    }

    private List<Described> describe(StageContext context, String work, MediaSvcClient.Trailer source,
                                     List<Window> windows) throws IOException {
        if (!Files.isRegularFile(source.file()) || windows.isEmpty()) {
            return List.of();
        }
        Path dir = claudeWork.resolve("creator").resolve(FilmClipFinder.slug(source.videoId()));
        Files.createDirectories(dir);
        int perSheet = COLS * ROWS;
        int perCall = perSheet * SHEETS_PER_CALL;
        List<Described> all = new ArrayList<>();
        for (int from = 0; from < windows.size(); from += perCall) {
            List<Path> sheets = new ArrayList<>();
            for (int first = from; first < Math.min(windows.size(), from + perCall); first += perSheet) {
                sheets.add(sheet(source, windows, first, Math.min(windows.size(), first + perSheet), dir));
            }
            try {
                Descriptions d = llm.json(context, Stage.ASSETS, "creator_describe", Map.of("work", work,
                        "sheets", sheets.stream().map(Path::toString).collect(Collectors.joining("\n"))),
                        Descriptions.class);
                if (d.tiles() != null) {
                    all.addAll(d.tiles());
                }
            } catch (RuntimeException e) {
                log.warn("Run {}: main video description batch failed: {}", context.run().id(), e.getMessage());
            }
        }
        return all;
    }

    /** A numbered contact sheet (4x3) of the windows' middle frames. Tile numbers count from 1 over all windows. */
    private Path sheet(MediaSvcClient.Trailer source, List<Window> windows, int first, int last, Path dir)
            throws IOException {
        BufferedImage sheet = new BufferedImage(COLS * TILE_W, ROWS * TILE_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Path frame = dir.resolve("frame.jpg");
        for (int i = first; i < last; i++) {
            Window w = windows.get(i);
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", String.format(Locale.ROOT, "%.3f",
                    (w.start() + w.end()) / 2), "-i", source.file().toString(), "-frames:v", "1", "-vf",
                    "scale=" + TILE_W + ":" + TILE_H + ":force_original_aspect_ratio=increase,crop=" + TILE_W + ":"
                            + TILE_H, frame.toString()));
            int x = ((i - first) % COLS) * TILE_W;
            int y = ((i - first) / COLS) * TILE_H;
            BufferedImage image = ImageIO.read(frame.toFile());
            if (image != null) {
                g.drawImage(image, x, y, null);
            }
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(x, y, 70, 40);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            g.drawString(String.valueOf(i + 1), x + 8, y + 30);
        }
        g.dispose();
        Files.deleteIfExists(frame);
        Path out = dir.resolve("sheet_" + (first + 1) + ".png");
        ImageIO.write(sheet, "png", out.toFile());
        return out;
    }

    /**
     * The main video for the editor, about every 10 seconds: what was said and what it showed (catalogue numbers for
     * exact moments, words for the rest). Only stretches that showed footage of the work are listed.
     */
    static String lines(Timeline timeline, List<VideoTranscript.Line> said, Function<Integer, Integer> tileOf) {
        double end = timeline.parts().stream().mapToDouble(Part::end).max().orElse(0);
        StringBuilder out = new StringBuilder();
        for (double a = 0; a < end; a += LINE_WINDOW) {
            double from = a;
            double to = a + LINE_WINDOW;
            Set<Integer> tiles = new LinkedHashSet<>();
            Set<String> words = new LinkedHashSet<>();
            for (Part part : timeline.parts()) {
                if (part.end() <= from || part.start() >= to) {
                    continue;
                }
                if (part.moment() != null) {
                    Integer tile = tileOf.apply(part.moment());
                    if (tile != null) {
                        tiles.add(tile);
                    }
                } else if (part.film() && words.size() < 2) {
                    words.add(part.shown());
                }
            }
            if (tiles.isEmpty() && words.isEmpty()) {
                continue;
            }
            String text = said == null ? "" : said.stream().filter(l -> l.start() >= from && l.start() < to)
                    .map(VideoTranscript.Line::text).collect(Collectors.joining(" "));
            String[] split = text.strip().split("\\s+");
            String spoken = String.join(" ", Arrays.copyOf(split, Math.min(split.length, MAX_SAID_WORDS)));
            out.append(String.format(Locale.ROOT, "[%d:%02d] said: \"%s\"", (int) from / 60, (int) from % 60,
                    spoken.replace('"', '\'')));
            if (!tiles.isEmpty()) {
                out.append(" | showed: ").append(tiles.stream().map(String::valueOf).collect(Collectors.joining(", ")));
            }
            if (!words.isEmpty()) {
                out.append(" | looks like: ").append(String.join("; ", words));
            }
            out.append('\n');
        }
        return out.toString().strip();
    }
}
