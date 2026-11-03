package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.revision.RevisionService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Real footage for videos about a film, series or anime, the way commentary channels work: the work's own scenes
 * are the main picture, matched to what the narrator says.
 * <ol>
 *   <li>Library: the work's trailers, teasers, TV spots, official clips and scene uploads (picture only, found by
 *       {@code clip_work} searches), plus any copy the owner put in {@code library/films}.</li>
 *   <li>Catalogue: every scene cut is shown to Claude on numbered contact sheets and described once
 *       ({@code clip_describe}); the catalogue is cached per work, so the next video on it costs nothing.</li>
 *   <li>Matching: one text call ({@code clip_match}) pairs narration shots with catalogue scenes.</li>
 *   <li>Cutting: each clip plays the scene (and the shots that follow it in the source) for the whole shot, muted,
 *       full screen, credited. Up to the owner's share of the video (70% by default).</li>
 * </ol>
 * Never done: mirroring, pitch or speed tricks to dodge Content ID. The protection is the commentary itself.
 */
@Component
public class FilmClipFinder {

    public record Work(String work, Integer year, List<String> queries) {
    }

    public record Match(String shot, Integer tile) {
    }

    public record Matches(List<Match> matches) {
    }

    public record Clip(String shotId, Path file, LicenseRecord license, String credit, String label,
                       double sourceSeconds) {
    }

    /** One scene cut of one source video. */
    record Segment(int tile, MediaSvcClient.Trailer source, double start, double end) {
        double length() {
            return end - start;
        }

        String key() {
            return source.videoId() + "@" + fmt(start);
        }
    }

    public record Described(Integer tile, String scene, Boolean text) {
    }

    public record Descriptions(List<Described> tiles) {
    }

    record CatalogEntry(String key, String scene, boolean text) {
    }

    private static final Logger log = LoggerFactory.getLogger(FilmClipFinder.class);
    private static final Pattern PTS = Pattern.compile("pts_time:([0-9.]+)");
    private static final Pattern CROP = Pattern.compile("crop=([0-9]+:[0-9]+:[0-9]+:[0-9]+)");
    private static final Pattern BLACK = Pattern.compile("black_duration:([0-9.]+)");
    private static final Pattern VIDEO_FILE = Pattern.compile("(?i).+\\.(mp4|mkv|webm|mov|avi)");
    static final double SLOWEST = 0.8;
    static final double TYPICAL_SHOT_SECONDS = 4;
    private static final int MAX_QUERIES = 8;
    private static final int VIDEOS_PER_QUERY = 2;
    private static final double LIBRARY_SECONDS = 45 * 60;
    private static final int MIN_LIBRARY_VIDEOS = 6;
    private static final double PROMO_SKIP_HEAD = 4;
    private static final double PROMO_SKIP_TAIL = 6;
    private static final double FILM_SKIP_HEAD = 60;
    private static final double FILM_SKIP_TAIL = 420;
    private static final double MIN_SEGMENT = 1.2;
    /** A longer "segment" usually hides a cut the detector missed in dark footage. */
    private static final double MAX_SEGMENT = 9;
    private static final int TILES_PER_PROMO = 45;
    private static final int TILES_PER_FILM = 220;
    private static final int MAX_TILES = 420;
    private static final int SHEETS_PER_CALL = 6;
    private static final int TILE_W = 320;
    private static final int TILE_H = 180;
    private static final int COLS = 4;
    private static final int ROWS = 3;

    private final LlmRouter llm;
    private final MediaSvcClient media;
    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final boolean enabled;
    private final Path library;
    private final Path films;
    private final Path claudeWork;

    public FilmClipFinder(LlmRouter llm, MediaSvcClient media, FfmpegService ffmpeg, JsonMapper json,
                          StoryForgeProperties properties,
                          @Value("${storyforge.clips.enabled:false}") boolean enabled) {
        this.llm = llm;
        this.media = media;
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.enabled = enabled;
        Path data = Path.of(properties.dataDir()).toAbsolutePath().resolve("library");
        this.library = data.resolve("trailers");
        this.films = data.resolve("films");
        this.claudeWork = Path.of(properties.llm().claudeCode().workDir()).toAbsolutePath();
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, Clip> find(StageContext context, ShotList shots, Alignment alignment, List<Scene> scenes,
                                  Set<String> excluded, int width, int height, double share) {
        if (!enabled || share <= 0) {
            return Map.of();
        }
        try {
            Work work = llm.json(context, Stage.ASSETS, "clip_work", Map.of("topic", context.run().topicTitle(),
                    "narration", narration(scenes)), Work.class);
            if (work.work() == null || work.work().isBlank() || work.queries() == null || work.queries().isEmpty()) {
                log.info("Run {}: not about a screen work, no footage", context.run().id());
                return Map.of();
            }
            Path dir = library.resolve(slug(work.work()));
            List<MediaSvcClient.Trailer> sources = sources(work, dir);
            List<Segment> segments = segments(sources, dir);
            if (segments.isEmpty()) {
                return Map.of();
            }
            Map<String, CatalogEntry> catalog = catalog(context, work, segments, dir);
            List<Segment> usable = segments.stream().filter(s -> {
                CatalogEntry entry = catalog.get(s.key());
                return entry != null && !entry.text();
            }).toList();
            List<Shot> candidates = shots.shots().stream()
                    .filter(s -> !s.graphic() && !excluded.contains(s.id()) && s.duration() <= 9).toList();
            int maxClips = maxClips(shots.durationSec(), share);
            if (candidates.isEmpty() || maxClips == 0 || usable.isEmpty()) {
                return Map.of();
            }
            String catalogLines = usable.stream().map(s -> s.tile() + " | " + fmt1(s.length()) + " s | "
                    + catalog.get(s.key()).scene()).collect(Collectors.joining("\n"));
            String shotLines = candidates.stream().map(s -> s.id() + " | " + fmt1(s.duration()) + " s | "
                    + RevisionService.spoken(alignment, s)).collect(Collectors.joining("\n"));
            Matches matches = llm.json(context, Stage.ASSETS, "clip_match", Map.of(
                    "work", label(work),
                    "catalog", catalogLines,
                    "shots", shotLines,
                    "max", String.valueOf(maxClips)), Matches.class);
            return cut(context, work, shots, segments, matches, maxClips, share, width, height);
        } catch (IOException | RuntimeException e) {
            log.warn("Run {}: film footage skipped: {}", context.run().id(), e.getMessage());
            return Map.of();
        }
    }

    static int maxClips(double durationSec, double share) {
        return (int) Math.floor(durationSec * share / TYPICAL_SHOT_SECONDS);
    }

    /** Applies the limits to the model's picks and renders each clip to exactly the shot's length. */
    private Map<String, Clip> cut(StageContext context, Work work, ShotList shots, List<Segment> segments,
                                  Matches matches, int maxClips, double share, int width, int height) {
        Map<String, Shot> byId = shots.shots().stream().collect(Collectors.toMap(Shot::id, s -> s));
        Map<Integer, Segment> byTile = segments.stream().collect(Collectors.toMap(Segment::tile, s -> s));
        List<Match> ordered = (matches.matches() == null ? List.<Match>of() : matches.matches()).stream()
                .filter(m -> m.shot() != null && m.tile() != null && byId.containsKey(m.shot())
                        && byTile.containsKey(m.tile()))
                .sorted((a, b) -> Double.compare(byId.get(a.shot()).start(), byId.get(b.shot()).start())).toList();
        Map<String, Clip> clips = new HashMap<>();
        Set<Integer> usedTiles = new HashSet<>();
        Set<String> usedShots = new HashSet<>();
        double total = 0;
        for (Match match : ordered) {
            Shot shot = byId.get(match.shot());
            Segment first = byTile.get(match.tile());
            if (clips.size() >= maxClips || usedTiles.contains(match.tile()) || !usedShots.add(shot.id())
                    || total + shot.duration() > shots.durationSec() * share) {
                continue;
            }
            double target = shot.duration() + 0.6;
            // The scene and the shots that follow it in the source, as long as they are back to back.
            List<Segment> run = run(segments, first, target);
            double available = run.get(run.size() - 1).end() - first.start() - 0.1;
            double source = Math.min(available, target);
            double factor = Math.max(SLOWEST, source / target);
            double hold = Math.max(0, target - source / factor);
            Path out = context.files().path(Stage.ASSETS, shot.id() + ".clip.mp4");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", fmt(first.start() + 0.05), "-t", fmt(source),
                    "-i", first.source().file().toString(), "-vf",
                    bars(first.source(), library.resolve(slug(work.work()))) + watermark(first.source())
                            + "setpts=PTS/" + fmt(factor)
                            + ",scale=" + width + ":" + height
                            + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height + ",fps=30"
                            + ",tpad=stop_mode=clone:stop_duration=" + fmt(hold + 0.1),
                    "-map", "0:v:0", "-map_metadata", "-1", "-write_tmcd", "0", "-an", "-dn", "-t", fmt(target),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p", out.toString()));
            if (mostlyBlack(out, target)) {
                // A fade-to-black or a night shot too dark to read: worse than any other picture.
                log.info("Run {} {}: footage too dark, left for another picture", context.run().id(), shot.id());
                usedTiles.add(match.tile());
                usedShots.remove(shot.id());
                try {
                    Files.deleteIfExists(out);
                } catch (IOException e) {
                    log.debug("Could not delete {}", out);
                }
                continue;
            }
            MediaSvcClient.Trailer from = first.source();
            String where = from.url() == null || from.url().isBlank() ? "owner's copy of the work" : from.url();
            String evidence = "Footage from \"" + from.title() + "\" (" + from.channel() + "), " + fmt(source)
                    + " s shown without audio under original commentary and criticism (fair use): " + where;
            clips.put(shot.id(), new Clip(shot.id(), out, new LicenseRecord("fair_use_clip",
                    "Footage: " + from.channel(), from.url(), evidence, null),
                    label(work), from.title(), source));
            run.forEach(s -> usedTiles.add(s.tile()));
            total += shot.duration();
        }
        log.info("Run {}: {} footage clip(s), {} s of {} s", context.run().id(), clips.size(), fmt(total),
                fmt(shots.durationSec()));
        return clips;
    }

    /** {@code first} plus the following back-to-back segments of the same source until {@code target} is covered. */
    private static List<Segment> run(List<Segment> segments, Segment first, double target) {
        List<Segment> run = new ArrayList<>(List.of(first));
        int index = segments.indexOf(first);
        while (run.get(run.size() - 1).end() - first.start() < target && index + 1 < segments.size() && run.size() < 4) {
            Segment next = segments.get(++index);
            Segment last = run.get(run.size() - 1);
            if (next.source() != first.source() || Math.abs(next.start() - last.end()) > 0.15) {
                break;
            }
            run.add(next);
        }
        return run;
    }

    /** The footage library of a work: reused when it is big enough, topped up from YouTube otherwise. */
    private List<MediaSvcClient.Trailer> sources(Work work, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path index = dir.resolve("sources.json");
        List<MediaSvcClient.Trailer> known = Files.isRegularFile(index)
                ? new ArrayList<>(json.readValue(index.toFile(), new TypeReference<List<MediaSvcClient.Trailer>>() { })
                        .stream().filter(t -> Files.isRegularFile(t.file())).toList())
                : new ArrayList<>();
        double seconds = known.stream().mapToDouble(MediaSvcClient.Trailer::durationSec).sum();
        if (known.size() < MIN_LIBRARY_VIDEOS) {
            for (String query : work.queries().stream().limit(MAX_QUERIES).toList()) {
                if (seconds >= LIBRARY_SECONDS) {
                    break;
                }
                try {
                    List<String> ids = known.stream().map(MediaSvcClient.Trailer::videoId).toList();
                    for (MediaSvcClient.Trailer video : media.footage(query, work.work(), dir, VIDEOS_PER_QUERY, ids)) {
                        if (Files.isRegularFile(video.file()) && known.stream()
                                .noneMatch(k -> k.videoId().equals(video.videoId()))) {
                            known.add(video);
                            seconds += video.durationSec();
                        }
                    }
                } catch (RuntimeException e) {
                    log.warn("No footage for '{}': {}", query, e.getMessage());
                }
            }
            json.writerWithDefaultPrettyPrinter().writeValue(index.toFile(), known);
        }
        List<MediaSvcClient.Trailer> all = new ArrayList<>(ownerCopies(work));
        all.addAll(known);
        log.info("Footage library for {}: {} video(s), {} min", work.work(), all.size(),
                Math.round(all.stream().mapToDouble(MediaSvcClient.Trailer::durationSec).sum() / 60));
        return all;
    }

    /** Films or episodes the owner put in library/films whose file name contains the work's title. */
    private List<MediaSvcClient.Trailer> ownerCopies(Work work) throws IOException {
        if (!Files.isDirectory(films)) {
            return List.of();
        }
        String title = slug(work.work());
        try (Stream<Path> files = Files.walk(films, 2)) {
            return files.filter(Files::isRegularFile)
                    .filter(f -> VIDEO_FILE.matcher(f.getFileName().toString()).matches())
                    .filter(f -> slug(f.getFileName().toString()).contains(title))
                    .map(f -> new MediaSvcClient.Trailer(f, "film-" + slug(f.getFileName().toString()),
                            f.getFileName().toString(), work.work(), "", ffmpeg.probe(f).durationSec()))
                    .toList();
        }
    }

    /** Scene cuts of every source (cached next to the video), minus logo heads and credit tails. */
    private List<Segment> segments(List<MediaSvcClient.Trailer> sources, Path dir) throws IOException {
        List<Segment> segments = new ArrayList<>();
        for (MediaSvcClient.Trailer source : sources) {
            boolean film = source.videoId().startsWith("film-");
            List<Double> cuts = cuts(source, dir);
            double duration = source.durationSec() > 0 ? source.durationSec() : cuts.get(cuts.size() - 1) + 2;
            double head = film ? FILM_SKIP_HEAD : PROMO_SKIP_HEAD;
            double tail = film ? FILM_SKIP_TAIL : PROMO_SKIP_TAIL;
            List<double[]> ranges = new ArrayList<>();
            for (int i = 0; i + 1 < cuts.size(); i++) {
                double start = cuts.get(i);
                double end = Math.min(cuts.get(i + 1), duration);
                if (start >= head && end <= duration - tail && end - start >= MIN_SEGMENT
                        && end - start <= MAX_SEGMENT) {
                    ranges.add(new double[] {start, end});
                }
            }
            int limit = film ? TILES_PER_FILM : TILES_PER_PROMO;
            double step = Math.max(1, ranges.size() / (double) limit);
            for (double i = 0; i < ranges.size() && segments.size() < MAX_TILES; i += step) {
                double[] range = ranges.get((int) i);
                segments.add(new Segment(segments.size() + 1, source, range[0], range[1]));
            }
        }
        return segments;
    }

    /**
     * Scene uploads by clip channels carry a corner logo (e.g. "MOVIECLIPS"): a slight centre crop removes it.
     */
    static String watermark(MediaSvcClient.Trailer source) {
        String text = ((source.channel() == null ? "" : source.channel()) + " " + (source.title() == null ? ""
                : source.title())).toLowerCase(Locale.ROOT);
        return text.contains("movieclips") || text.contains("movie clips") ? "crop=iw*0.86:ih*0.86," : "";
    }

    /** True when more than a third of the clip is (near) black. */
    private static final double DARK_STRETCH_SECONDS = 0.5;

    private boolean mostlyBlack(Path clip, double length) {
        try {
            String output = ffmpeg.ffmpeg(List.of("-i", clip.toString(), "-an", "-vf",
                    "blackdetect=d=0.2:pix_th=0.10", "-f", "null", "-"));
            Matcher matcher = BLACK.matcher(output);
            double black = 0;
            double longest = 0;
            while (matcher.find()) {
                double segment = Double.parseDouble(matcher.group(1));
                black += segment;
                longest = Math.max(longest, segment);
            }
            // QA blocks any black stretch of half a second (video.black_frames), so one such moment is enough.
            return black > length / 3 || longest >= DARK_STRETCH_SECONDS;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A crop filter that removes black letterbox bars baked into the source (common in trailers), so the footage
     * really fills the screen; empty when there are none. Measured once per source on a few seconds of its middle.
     */
    private String bars(MediaSvcClient.Trailer source, Path dir) {
        Path cache = dir.resolve(slug(source.videoId()) + ".crop.txt");
        try {
            if (Files.isRegularFile(cache)) {
                return Files.readString(cache);
            }
            double middle = Math.max(0, source.durationSec() / 2 - 5);
            String output = ffmpeg.ffmpeg(List.of("-ss", fmt(middle), "-t", "10", "-i", source.file().toString(),
                    "-an", "-vf", "cropdetect=limit=24:round=2:reset=0", "-f", "null", "-"));
            Matcher matcher = CROP.matcher(output);
            String crop = null;
            while (matcher.find()) {
                crop = matcher.group(1);
            }
            MediaInfo info = ffmpeg.probe(source.file());
            String filter = "";
            if (crop != null && info.height() > 0) {
                String[] parts = crop.split(":");
                int h = Integer.parseInt(parts[1]);
                int w = Integer.parseInt(parts[0]);
                // Only real bars: at least 4% of the height removed, and the width kept.
                if (h < info.height() * 0.96 && h > info.height() * 0.5 && w >= info.width() * 0.96) {
                    filter = "crop=" + crop + ",";
                }
            }
            Files.writeString(cache, filter);
            return filter;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private List<Double> cuts(MediaSvcClient.Trailer source, Path dir) throws IOException {
        Path cache = dir.resolve(slug(source.videoId()) + ".cuts.json");
        if (Files.isRegularFile(cache)) {
            return json.readValue(cache.toFile(), new TypeReference<List<Double>>() { });
        }
        String output = ffmpeg.ffmpeg(List.of("-i", source.file().toString(), "-an", "-vf",
                "select='gt(scene,0.18)',showinfo", "-f", "null", "-"));
        List<Double> cuts = new ArrayList<>(List.of(0.0));
        Matcher matcher = PTS.matcher(output);
        while (matcher.find()) {
            cuts.add(Double.parseDouble(matcher.group(1)));
        }
        cuts.add(source.durationSec() > 0 ? source.durationSec() : cuts.get(cuts.size() - 1) + 2);
        json.writeValue(cache.toFile(), cuts);
        return cuts;
    }

    /** What every segment shows, described by Claude from contact sheets; cached per work. */
    private Map<String, CatalogEntry> catalog(StageContext context, Work work, List<Segment> segments, Path dir)
            throws IOException {
        Path file = dir.resolve("catalog.json");
        Map<String, CatalogEntry> catalog = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            json.readValue(file.toFile(), new TypeReference<List<CatalogEntry>>() { })
                    .forEach(entry -> catalog.put(entry.key(), entry));
        }
        List<Segment> missing = segments.stream().filter(s -> !catalog.containsKey(s.key())).toList();
        if (missing.isEmpty()) {
            return catalog;
        }
        Path sheetDir = claudeWork.resolve("clips").resolve(slug(work.work()));
        int perCall = SHEETS_PER_CALL * COLS * ROWS;
        for (int from = 0; from < missing.size(); from += perCall) {
            List<Segment> batch = missing.subList(from, Math.min(missing.size(), from + perCall));
            List<Path> sheets = sheets(batch, sheetDir);
            try {
                Descriptions described = llm.json(context, Stage.ASSETS, "clip_describe", Map.of(
                        "work", label(work),
                        "sheets", sheets.stream().map(Path::toString).collect(Collectors.joining("\n"))),
                        Descriptions.class);
                Map<Integer, Segment> byTile = batch.stream().collect(Collectors.toMap(Segment::tile, s -> s));
                for (Described d : described.tiles() == null ? List.<Described>of() : described.tiles()) {
                    Segment segment = d.tile() == null ? null : byTile.get(d.tile());
                    if (segment != null && d.scene() != null && !d.scene().isBlank()) {
                        catalog.put(segment.key(), new CatalogEntry(segment.key(), d.scene().strip(),
                                Boolean.TRUE.equals(d.text())));
                    }
                }
            } catch (RuntimeException e) {
                log.warn("Run {}: footage description batch failed: {}", context.run().id(), e.getMessage());
            }
        }
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(catalog.values()));
        log.info("Footage catalogue for {}: {} scene(s) described", work.work(), catalog.size());
        return catalog;
    }

    /** Numbered contact sheets (4x3 tiles) of each segment's middle frame, for the model to look at. */
    private List<Path> sheets(List<Segment> segments, Path dir) throws IOException {
        Files.createDirectories(dir);
        List<Path> sheets = new ArrayList<>();
        int perSheet = COLS * ROWS;
        for (int first = 0; first < segments.size(); first += perSheet) {
            BufferedImage sheet = new BufferedImage(COLS * TILE_W, ROWS * TILE_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sheet.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            for (int i = first; i < Math.min(segments.size(), first + perSheet); i++) {
                Segment segment = segments.get(i);
                Path frame = dir.resolve("tile_" + segment.tile() + ".jpg");
                ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", fmt((segment.start() + segment.end()) / 2),
                        "-i", segment.source().file().toString(), "-frames:v", "1", "-vf",
                        "scale=" + TILE_W + ":" + TILE_H + ":force_original_aspect_ratio=increase,crop=" + TILE_W
                                + ":" + TILE_H, frame.toString()));
                int x = ((i - first) % COLS) * TILE_W;
                int y = ((i - first) / COLS) * TILE_H;
                g.drawImage(ImageIO.read(frame.toFile()), x, y, null);
                g.setColor(new Color(0, 0, 0, 190));
                g.fillRect(x, y, 70, 40);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
                g.drawString(String.valueOf(segment.tile()), x + 8, y + 30);
                g.setStroke(new BasicStroke(2));
                g.drawRect(x, y, TILE_W - 1, TILE_H - 1);
            }
            g.dispose();
            Path out = dir.resolve("sheet_" + segments.get(first).tile() + ".png");
            ImageIO.write(sheet, "png", out.toFile());
            sheets.add(out);
        }
        return sheets;
    }

    private static String label(Work work) {
        return work.work() + (work.year() == null ? "" : " (" + work.year() + ")");
    }

    static String slug(String text) {
        return text.replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
    }

    private static String narration(List<Scene> scenes) {
        String text = scenes.stream().map(Scene::narration).filter(n -> n != null)
                .collect(Collectors.joining(" "));
        return text.length() > 2500 ? text.substring(0, 2500) : text;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
