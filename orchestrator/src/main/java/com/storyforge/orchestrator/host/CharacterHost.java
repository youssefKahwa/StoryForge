package com.storyforge.orchestrator.host;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The channel's masked host in short "talking" moments. The mask has no mouth, so no lip-sync is needed: hand
 * gestures and jump cuts read as speech. Every appearance is a new edit, never a replayed clip:
 * <ul>
 *   <li>Library (once per clip): the owner's gesture clips (Veo, Grok...) in {@code BiPathVideos} are upscaled to
 *       1080p (Real-ESRGAN), and Claude tags every 1-4 s segment with its gesture and whether the studio, mask and
 *       hands still look right (AI clips drift).</li>
 *   <li>Each moment: 1-3 segments chosen by meaning (a question gets a shrug, a twist a lean...) and by how rarely
 *       they were used before, each with its own framing (wide, medium, close: a jump-cut talking head), its own
 *       start point and a slight speed change, cut on the narrator's word boundaries.</li>
 *   <li>Without clips, the still poses ({@code char1..}) are jump-cut the same way.</li>
 * </ul>
 */
@Component
public class CharacterHost {

    public record Segment(String clip, String file, double from, double to, String gesture) {
        double length() {
            return to - from;
        }

        String key() {
            return clip + "@" + from;
        }
    }

    public record Tag(Double from, Double to, String gesture, Boolean usable) {
    }

    public record Tags(List<Tag> segments) {
    }

    private record Piece(Segment segment, Path still, double length, double speed, double zoom) {
    }

    private static final Logger log = LoggerFactory.getLogger(CharacterHost.class);
    private static final double[] ZOOMS = {1.0, 1.17, 1.36};
    /** Where the mask and hands sit in the owner's framing (fraction of the frame), for the closer framings. */
    private static final double FOCUS_X = 0.58;
    private static final double FOCUS_Y = 0.42;
    private static final double SHEET_STEP = 0.5;
    private static final double MIN_SEGMENT = 1.0;
    private static final int W = 1920;
    private static final int H = 1080;

    private final FfmpegService ffmpeg;
    private final LlmRouter llm;
    private final JsonMapper json;
    private final Path characterDir;
    private final Path cache;
    private final Path claudeWork;
    private final StoryForgeProperties.Upscaler upscaler;

    public CharacterHost(FfmpegService ffmpeg, LlmRouter llm, JsonMapper json, StoryForgeProperties properties,
                         @Value("${storyforge.character.dir:../charachterBipath/Charachter}") String characterDir) {
        this.ffmpeg = ffmpeg;
        this.llm = llm;
        this.json = json;
        this.characterDir = Path.of(characterDir).toAbsolutePath().normalize();
        this.cache = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("host-clips");
        this.claudeWork = Path.of(properties.llm().claudeCode().workDir()).toAbsolutePath();
        this.upscaler = properties.upscaler();
    }

    public boolean available() {
        return !stills().isEmpty() || !clips().isEmpty();
    }

    /**
     * Picks the host moments of a video and renders each one as a new edit; returns shot id to video file.
     * {@code excluded}: shots that already show something better (the work's own footage, maps).
     */
    public Map<String, Path> moments(StageContext context, ShotList shots, Alignment alignment, Set<String> excluded) {
        if (!available()) {
            return Map.of();
        }
        List<HostMoments.Moment> moments = HostMoments.pick(shots, alignment, excluded);
        if (moments.isEmpty()) {
            return Map.of();
        }
        List<Segment> segments = segments(context);
        List<Path> stills = preparedStills();
        Map<String, Integer> usage = usage();
        Map<String, Path> out = new LinkedHashMap<>();
        long seed = context.run().id() * 7919L;
        for (HostMoments.Moment moment : moments) {
            Path file = context.files().path(Stage.ASSETS, moment.shotId() + ".host.mp4");
            try {
                List<Double> cuts = cutPoints(moment, alignment);
                compose(plan(moment, cuts, segments, stills, usage, seed++), file, context.files().dir(Stage.ASSETS));
                out.put(moment.shotId(), file);
            } catch (RuntimeException e) {
                log.warn("Run {}: host moment {} skipped: {}", context.run().id(), moment.shotId(), e.getMessage());
            }
        }
        saveUsage(usage);
        log.info("Run {}: {} host moment(s) from {} gesture segment(s) and {} still(s)", context.run().id(),
                out.size(), segments.size(), stills.size());
        return out;
    }

    /** Piece boundaries inside the moment: about every 1.4-2.4 s, moved to the nearest gap between words. */
    static List<Double> cutPoints(HostMoments.Moment moment, Alignment alignment) {
        double length = moment.length();
        int pieces = length < 2.6 ? 1 : length < 4.6 ? 2 : 3;
        List<Double> gaps = alignment.words().stream()
                .filter(w -> w.end() > moment.start() + 0.8 && w.end() < moment.end() - 0.8)
                .map(w -> w.end() - moment.start()).toList();
        List<Double> cuts = new ArrayList<>();
        for (int i = 1; i < pieces; i++) {
            double ideal = length * i / pieces;
            double best = ideal;
            double distance = 0.6;
            for (double gap : gaps) {
                if (Math.abs(gap - ideal) < distance) {
                    best = gap;
                    distance = Math.abs(gap - ideal);
                }
            }
            cuts.add(best);
        }
        return cuts;
    }

    private List<Piece> plan(HostMoments.Moment moment, List<Double> cuts, List<Segment> segments, List<Path> stills,
                             Map<String, Integer> usage, long seed) {
        List<Double> bounds = new ArrayList<>(List.of(0.0));
        bounds.addAll(cuts);
        bounds.add(moment.length());
        List<Piece> pieces = new ArrayList<>();
        int framing = (int) Math.floorMod(seed, ZOOMS.length);
        Segment previous = null;
        Path previousStill = null;
        for (int i = 0; i + 1 < bounds.size(); i++) {
            double length = bounds.get(i + 1) - bounds.get(i);
            double zoom = ZOOMS[(framing + i) % ZOOMS.length];
            double speed = 0.94 + Math.floorMod(seed * 31 + i, 13) / 100.0;
            Segment pick = choose(segments, moment.intent(), usage, previous, seed + i);
            if (pick != null) {
                usage.merge(pick.key(), 1, Integer::sum);
                pieces.add(new Piece(pick, null, length, speed, zoom));
                previous = pick;
            } else if (!stills.isEmpty()) {
                Path still = stills.get((int) Math.floorMod(seed + i * 3L, stills.size()));
                if (still.equals(previousStill) && stills.size() > 1) {
                    still = stills.get((stills.indexOf(still) + 1) % stills.size());
                }
                pieces.add(new Piece(null, still, length, 1, zoom));
                previousStill = still;
            }
        }
        if (pieces.isEmpty()) {
            throw new IllegalStateException("no host material");
        }
        return pieces;
    }

    /** Same gesture first, then the least used segments; never the segment just shown. */
    private static Segment choose(List<Segment> segments, String intent, Map<String, Integer> usage, Segment previous,
                                  long seed) {
        return segments.stream().filter(s -> previous == null || !s.key().equals(previous.key()))
                .min(Comparator.<Segment>comparingInt(s -> s.gesture().equals(intent) ? 0
                                : "explain".equals(s.gesture()) || "neutral".equals(s.gesture()) ? 1 : 2)
                        .thenComparingInt(s -> usage.getOrDefault(s.key(), 0))
                        .thenComparingLong(s -> Math.floorMod((s.key().hashCode() * 31L) ^ seed, 1000)))
                .orElse(null);
    }

    private void compose(List<Piece> pieces, Path out, Path workDir) {
        List<Path> parts = new ArrayList<>();
        try {
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                Path part = workDir.resolve(out.getFileName() + ".part" + i + ".mp4");
                String crop = f("crop=iw/%.3f:ih/%.3f:(iw-iw/%.3f)*%.3f:(ih-ih/%.3f)*%.3f,scale=%d:%d:flags=lanczos,"
                                + "setsar=1,fps=30", piece.zoom(), piece.zoom(), piece.zoom(), FOCUS_X, piece.zoom(),
                        FOCUS_Y, W, H);
                List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error"));
                if (piece.segment() != null) {
                    Segment s = piece.segment();
                    double need = piece.length() * piece.speed();
                    double room = Math.max(0, s.length() - need);
                    double offset = room * Math.floorMod(s.key().hashCode() + i * 17, 100) / 100.0;
                    args.addAll(List.of("-ss", f("%.3f", s.from() + offset), "-t", f("%.3f", need + 0.2),
                            "-i", s.file(), "-vf", f("setpts=PTS/%.3f,%s,tpad=stop_mode=clone:stop_duration=2",
                                    piece.speed(), crop)));
                } else {
                    args.addAll(List.of("-loop", "1", "-t", f("%.3f", piece.length() + 0.2), "-i",
                            piece.still().toString(), "-vf", crop));
                }
                args.addAll(List.of("-t", f("%.3f", piece.length()), "-an", "-c:v", "libx264", "-preset", "veryfast",
                        "-crf", "19", "-pix_fmt", "yuv420p", part.toString()));
                ffmpeg.ffmpeg(args);
                parts.add(part);
            }
            Path list = workDir.resolve(out.getFileName() + ".txt");
            Files.writeString(list, String.join("\n", parts.stream()
                    .map(p -> "file '" + p.toString().replace("\\", "/") + "'").toList()), StandardCharsets.UTF_8);
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", list.toString(),
                    "-c", "copy", out.toString()));
            Files.deleteIfExists(list);
            for (Path part : parts) {
                Files.deleteIfExists(part);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Usable, tagged segments of every gesture clip (prepared once and cached). */
    private List<Segment> segments(StageContext context) {
        List<Segment> segments = new ArrayList<>();
        for (Path clip : clips()) {
            try {
                String id = id(clip);
                Path master = cache.resolve(id + ".mp4");
                if (!Files.isRegularFile(master)) {
                    master(clip, master);
                }
                Path tagsFile = cache.resolve(id + ".tags.json");
                Tags tags = Files.isRegularFile(tagsFile) ? json.readValue(tagsFile.toFile(), Tags.class) : null;
                if (tags == null) {
                    tags = tag(context, id, master);
                    json.writerWithDefaultPrettyPrinter().writeValue(tagsFile.toFile(), tags);
                }
                for (Tag tag : tags.segments() == null ? List.<Tag>of() : tags.segments()) {
                    if (tag.from() != null && tag.to() != null && tag.to() - tag.from() >= MIN_SEGMENT
                            && !Boolean.FALSE.equals(tag.usable())) {
                        segments.add(new Segment(id, master.toString(), tag.from(), tag.to(),
                                tag.gesture() == null ? "explain" : tag.gesture().toLowerCase(Locale.ROOT)));
                    }
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Host clip {} skipped: {}", clip.getFileName(), e.getMessage());
            }
        }
        return segments;
    }

    /** A 1080p, 30 fps, silent master of a gesture clip; small AI clips are upscaled with Real-ESRGAN first. */
    private void master(Path clip, Path master) throws IOException {
        Files.createDirectories(cache);
        MediaInfo info = ffmpeg.probe(clip);
        String fit = f("scale=%d:%d:force_original_aspect_ratio=increase:flags=lanczos,crop=%d:%d,setsar=1,fps=30",
                W, H, W, H);
        Path exe = upscaler == null || upscaler.executable() == null ? null : Path.of(upscaler.executable());
        if (info.height() > 0 && info.height() < 1000 && exe != null && Files.isRegularFile(exe)) {
            int scale = (int) Math.min(4, Math.max(2, Math.ceil(1080.0 / info.height())));
            Path work = cache.resolve("tmp-" + master.getFileName());
            Path in = work.resolve("in");
            Path up = work.resolve("out");
            Files.createDirectories(in);
            Files.createDirectories(up);
            try {
                ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", clip.toString(), in.resolve("f%05d.png")
                        .toString()));
                log.info("Upscaling host clip {} x{} ({} px high), once", clip.getFileName(), scale, info.height());
                Process process = new ProcessBuilder(exe.toString(), "-i", in.toString(), "-o", up.toString(),
                        "-n", "realesr-animevideov3", "-s", String.valueOf(scale), "-f", "png")
                        .directory(exe.getParent().toFile()).redirectErrorStream(true)
                        .redirectOutput(work.resolve("esrgan.log").toFile()).start();
                if (!process.waitFor(40, TimeUnit.MINUTES) || process.exitValue() != 0) {
                    process.destroyForcibly();
                    throw new IOException("Real-ESRGAN failed on " + clip.getFileName());
                }
                double fps = info.fps() > 0 ? info.fps() : 24;
                ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-framerate", f("%.3f", fps), "-i",
                        up.resolve("f%05d.png").toString(), "-vf", fit, "-an", "-c:v", "libx264", "-crf", "17",
                        "-preset", "medium", "-pix_fmt", "yuv420p", master.toString()));
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } catch (IOException | RuntimeException e) {
                log.warn("Upscale of {} failed, using plain scaling: {}", clip.getFileName(), e.getMessage());
            } finally {
                deleteTree(work);
            }
        }
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", clip.toString(), "-vf", fit, "-an", "-c:v",
                "libx264", "-crf", "17", "-preset", "medium", "-pix_fmt", "yuv420p", master.toString()));
    }

    /** Claude looks at a contact sheet of the clip and logs gestures and unusable (drifting) moments. */
    private Tags tag(StageContext context, String id, Path master) throws IOException {
        double length = ffmpeg.probe(master).durationSec();
        Path dir = claudeWork.resolve("host").resolve(id);
        Files.createDirectories(dir);
        List<Double> times = new ArrayList<>();
        for (double t = 0.1; t < length - 0.05; t += SHEET_STEP) {
            times.add(t);
        }
        int cols = 4;
        int tileW = 320;
        int tileH = 180;
        int rows = (times.size() + cols - 1) / cols;
        BufferedImage sheet = new BufferedImage(cols * tileW, rows * tileH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < times.size(); i++) {
            Path frame = dir.resolve("t" + i + ".jpg");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", f("%.3f", times.get(i)), "-i", master.toString(),
                    "-frames:v", "1", "-vf", "scale=" + tileW + ":" + tileH, frame.toString()));
            int x = (i % cols) * tileW;
            int y = (i / cols) * tileH;
            g.drawImage(ImageIO.read(frame.toFile()), x, y, null);
            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(x, y, 70, 32);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.drawString(f("%.1fs", times.get(i)), x + 6, y + 24);
            Files.deleteIfExists(frame);
        }
        g.dispose();
        Path sheetFile = dir.resolve("sheet.png");
        ImageIO.write(sheet, "png", sheetFile.toFile());
        Path reference = dir.resolve("reference.png");
        List<Path> stills = preparedStills();
        if (!stills.isEmpty()) {
            Files.copy(stills.get(0), reference, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            return llm.json(context, Stage.ASSETS, "host_tags", Map.of(
                    "reference", Files.isRegularFile(reference) ? reference.toString() : "(none)",
                    "sheet", sheetFile.toString(),
                    "step", f("%.1f", SHEET_STEP),
                    "length", f("%.1f", length)), Tags.class);
        } catch (RuntimeException e) {
            log.warn("Host clip {} could not be tagged, using it as plain explaining gestures: {}", id, e.getMessage());
            List<Tag> tags = new ArrayList<>();
            for (double t = 0; t + MIN_SEGMENT <= length; t += 2) {
                tags.add(new Tag(t, Math.min(length, t + 2), "explain", true));
            }
            return new Tags(tags);
        }
    }

    /** The still poses (char1, char2...) as 1080p frames, prepared once. */
    private List<Path> preparedStills() {
        List<Path> out = new ArrayList<>();
        for (Path still : stills()) {
            Path prepared = cache.resolve("stills").resolve(still.getFileName().toString()
                    .replaceAll("\\.[^.]+$", "") + ".png");
            try {
                if (!Files.isRegularFile(prepared)) {
                    Files.createDirectories(prepared.getParent());
                    ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", still.toString(), "-vf",
                            f("scale=%d:%d:force_original_aspect_ratio=increase:flags=lanczos,crop=%d:%d", W, H, W, H),
                            "-frames:v", "1", prepared.toString()));
                }
                out.add(prepared);
            } catch (IOException | RuntimeException e) {
                log.warn("Host still {} skipped: {}", still.getFileName(), e.getMessage());
            }
        }
        return out;
    }

    private List<Path> stills() {
        return list(characterDir, "(?i)char\\d+\\.(jpg|jpeg|png|webp)");
    }

    private List<Path> clips() {
        return list(characterDir.resolve("BiPathVideos"), "(?i).+\\.(mp4|mov|webm|mkv)");
    }

    private static List<Path> list(Path dir, String pattern) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile).filter(f -> f.getFileName().toString().matches(pattern))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Map<String, Integer> usage() {
        Path file = cache.resolve("usage.json");
        if (Files.isRegularFile(file)) {
            try {
                return new HashMap<>(json.readValue(file.toFile(), new TypeReference<Map<String, Integer>>() { }));
            } catch (RuntimeException e) {
                log.debug("Host usage unreadable: {}", e.getMessage());
            }
        }
        return new HashMap<>();
    }

    private void saveUsage(Map<String, Integer> usage) {
        try {
            Files.createDirectories(cache);
            json.writeValue(cache.resolve("usage.json").toFile(), usage);
        } catch (IOException | RuntimeException e) {
            log.debug("Host usage not saved: {}", e.getMessage());
        }
    }

    static String id(Path clip) throws IOException {
        return clip.getFileName().toString().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9]+", "_")
                .toLowerCase(Locale.ROOT) + "-" + Files.size(clip);
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.debug("Could not delete {}", p);
                }
            });
        } catch (IOException e) {
            log.debug("Could not clean {}", dir);
        }
    }

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }
}
