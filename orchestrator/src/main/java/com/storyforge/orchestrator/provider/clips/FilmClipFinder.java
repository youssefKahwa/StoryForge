package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fair-use commentary clips, fully automatic: when the story is about a film/series/anime, the official trailer is
 * found and downloaded without audio, cut into shots, and Claude matches trailer shots to narration moments by
 * looking at numbered contact sheets. Hard limits keep the use short and transformative: at most 15% of the video,
 * each clip <= 4.5 s of source (slow motion, then a freeze frame), never back to back, muted, framed, credited.
 */
@Component
public class FilmClipFinder {

    public record Work(String work, Integer year, List<String> queries) {
    }

    public record Match(String shot, Integer tile) {
    }

    public record Matches(List<Match> matches, List<Integer> textTiles) {
    }

    public record Clip(String shotId, Path file, LicenseRecord license, String credit, String label,
                       double sourceSeconds) {
    }

    record Segment(int tile, MediaSvcClient.Trailer trailer, double start, double end) {
        double length() {
            return end - start;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(FilmClipFinder.class);
    private static final Pattern PTS = Pattern.compile("pts_time:([0-9.]+)");
    static final double MAX_SHARE = 0.15;
    static final double MAX_SOURCE_SECONDS = 4.5;
    static final double SLOWEST = 0.5;
    private static final double TYPICAL_SHOT_SECONDS = 5;
    static final double MIN_GAP_SECONDS = 15;
    private static final double SKIP_HEAD = 5;
    private static final double SKIP_TAIL = 8;
    private static final double MIN_SEGMENT = 1.6;
    private static final int TILE_W = 320;
    private static final int TILE_H = 180;
    private static final int COLS = 4;
    private static final int ROWS = 3;
    private static final int MAX_TILES = 48;

    private final LlmRouter llm;
    private final MediaSvcClient media;
    private final FfmpegService ffmpeg;
    private final boolean enabled;
    private final Path trailers;
    private final Path claudeWork;

    public FilmClipFinder(LlmRouter llm, MediaSvcClient media, FfmpegService ffmpeg, StoryForgeProperties properties,
                          @Value("${storyforge.clips.enabled:false}") boolean enabled) {
        this.llm = llm;
        this.media = media;
        this.ffmpeg = ffmpeg;
        this.enabled = enabled;
        this.trailers = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("trailers");
        this.claudeWork = Path.of(properties.llm().claudeCode().workDir()).toAbsolutePath();
    }

    public boolean enabled() {
        return enabled;
    }

    public Map<String, Clip> find(StageContext context, ShotList shots, Alignment alignment, List<Scene> scenes,
                                  Set<String> excluded, int width, int height) {
        if (!enabled) {
            return Map.of();
        }
        try {
            Work work = llm.json(context, Stage.ASSETS, "clip_work", Map.of("topic", context.run().topicTitle(),
                    "narration", narration(scenes)), Work.class);
            if (work.work() == null || work.work().isBlank() || work.queries() == null || work.queries().isEmpty()) {
                log.info("Run {}: not about a screen work, no clips", context.run().id());
                return Map.of();
            }
            List<MediaSvcClient.Trailer> found = trailers(work);
            if (found.isEmpty()) {
                return Map.of();
            }
            List<Segment> segments = segments(found);
            if (segments.isEmpty()) {
                return Map.of();
            }
            Path sheetDir = claudeWork.resolve("clips").resolve("run-" + context.run().id());
            List<Path> sheets = sheets(segments, sheetDir);
            List<Shot> candidates = shots.shots().stream()
                    .filter(s -> !s.graphic() && !excluded.contains(s.id()) && s.start() > 1)
                    .filter(s -> s.duration() <= 8).toList();
            int maxClips = maxClips(shots.durationSec());
            if (candidates.isEmpty() || maxClips == 0) {
                return Map.of();
            }
            String lines = candidates.stream().map(s -> s.id() + " | " + RevisionService.spoken(alignment, s))
                    .collect(Collectors.joining("\n"));
            Matches matches = llm.json(context, Stage.ASSETS, "clip_match", Map.of(
                    "work", work.work() + (work.year() == null ? "" : " (" + work.year() + ")"),
                    "sheets", sheets.stream().map(Path::toString).collect(Collectors.joining("\n")),
                    "tiles", String.valueOf(segments.size()),
                    "shots", lines,
                    "max", String.valueOf(maxClips)), Matches.class);
            return cut(context, work, shots, segments, matches, maxClips, width, height);
        } catch (IOException | RuntimeException e) {
            log.warn("Run {}: film clips skipped: {}", context.run().id(), e.getMessage());
            return Map.of();
        }
    }

    static int maxClips(double durationSec) {
        return (int) Math.floor(durationSec * MAX_SHARE / TYPICAL_SHOT_SECONDS);
    }

    /** Applies the hard limits to the model's picks and renders each clip to exactly the shot's length. */
    private Map<String, Clip> cut(StageContext context, Work work, ShotList shots, List<Segment> segments,
                                  Matches matches, int maxClips, int width, int height) {
        Map<String, Shot> byId = shots.shots().stream().collect(Collectors.toMap(Shot::id, s -> s));
        Set<Integer> textTiles = matches.textTiles() == null ? Set.of() : Set.copyOf(matches.textTiles());
        List<Match> ordered = (matches.matches() == null ? List.<Match>of() : matches.matches()).stream()
                .filter(m -> m.shot() != null && m.tile() != null && byId.containsKey(m.shot()))
                .filter(m -> m.tile() >= 1 && m.tile() <= segments.size() && !textTiles.contains(m.tile()))
                .sorted((a, b) -> Double.compare(byId.get(a.shot()).start(), byId.get(b.shot()).start())).toList();
        Map<String, Clip> clips = new HashMap<>();
        Set<Integer> usedTiles = new HashSet<>();
        double lastEnd = -100;
        double total = 0;
        for (Match match : ordered) {
            Shot shot = byId.get(match.shot());
            Segment segment = segments.get(match.tile() - 1);
            double target = shot.duration() + 0.6;
            double source = Math.min(Math.min(segment.length() - 0.2, MAX_SOURCE_SECONDS), target);
            double factor = Math.max(SLOWEST, source / target);
            double hold = Math.max(0, target - source / factor);
            if (clips.size() >= maxClips || usedTiles.contains(match.tile()) || source < 1.2
                    || shot.start() - lastEnd < MIN_GAP_SECONDS
                    || total + shot.duration() > shots.durationSec() * MAX_SHARE) {
                continue;
            }
            Path out = context.files().path(Stage.ASSETS, shot.id() + ".clip.mp4");
            // Slow motion, then a freeze on the last frame while the narrator keeps commenting.
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-ss", fmt(segment.start() + 0.1), "-t", fmt(source),
                    "-i", segment.trailer().file().toString(), "-vf",
                    "setpts=PTS/" + fmt(factor) + ",scale=" + width + ":" + height
                            + ":force_original_aspect_ratio=increase,crop=" + width + ":" + height + ",fps=30"
                            + ",tpad=stop_mode=clone:stop_duration=" + fmt(hold + 0.1),
                    "-an", "-t", fmt(target), "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                    "-pix_fmt", "yuv420p", out.toString()));
            MediaSvcClient.Trailer trailer = segment.trailer();
            String evidence = "Clip from \"" + trailer.title() + "\" (" + trailer.channel() + "), "
                    + fmt(source) + " s shown without audio for commentary and criticism (fair use): " + trailer.url();
            clips.put(shot.id(), new Clip(shot.id(), out, new LicenseRecord("fair_use_clip",
                    "Official trailer: " + trailer.channel(), trailer.url(), evidence, null),
                    "Clip: " + work.work() + " · trailer", trailer.title(), source));
            usedTiles.add(match.tile());
            lastEnd = shot.end();
            total += shot.duration();
        }
        log.info("Run {}: {} fair-use clip(s), {} s of {} s", context.run().id(), clips.size(), fmt(total),
                fmt(shots.durationSec()));
        return clips;
    }

    private List<MediaSvcClient.Trailer> trailers(Work work) {
        Path dir = trailers.resolve(work.work().replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT));
        List<MediaSvcClient.Trailer> found = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String query : work.queries().stream().limit(3).toList()) {
            try {
                MediaSvcClient.Trailer trailer = media.trailer(query, work.work(), dir);
                if (ids.add(trailer.videoId()) && Files.isRegularFile(trailer.file())) {
                    found.add(trailer);
                }
            } catch (RuntimeException e) {
                log.warn("No trailer for '{}': {}", query, e.getMessage());
            }
        }
        return found;
    }

    /** Shots of each trailer from scene-change detection, minus the logo/title-card head and the credits tail. */
    private List<Segment> segments(List<MediaSvcClient.Trailer> found) {
        List<Segment> segments = new ArrayList<>();
        for (MediaSvcClient.Trailer trailer : found) {
            String output = ffmpeg.ffmpeg(List.of("-i", trailer.file().toString(), "-an", "-vf",
                    "select='gt(scene,0.32)',showinfo", "-f", "null", "-"));
            List<Double> cuts = new ArrayList<>(List.of(0.0));
            Matcher matcher = PTS.matcher(output);
            while (matcher.find()) {
                cuts.add(Double.parseDouble(matcher.group(1)));
            }
            double duration = trailer.durationSec() > 0 ? trailer.durationSec() : cuts.get(cuts.size() - 1) + 2;
            cuts.add(duration);
            for (int i = 0; i + 1 < cuts.size() && segments.size() < MAX_TILES; i++) {
                double start = cuts.get(i);
                double end = cuts.get(i + 1);
                if (start >= SKIP_HEAD && end <= duration - SKIP_TAIL && end - start >= MIN_SEGMENT) {
                    segments.add(new Segment(segments.size() + 1, trailer, start, end));
                }
            }
        }
        return segments;
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
                        "-i", segment.trailer().file().toString(), "-frames:v", "1", "-vf",
                        "scale=" + TILE_W + ":" + TILE_H + ":force_original_aspect_ratio=increase,crop=" + TILE_W
                                + ":" + TILE_H, frame.toString()));
                int x = ((i - first) % COLS) * TILE_W;
                int y = ((i - first) / COLS) * TILE_H;
                g.drawImage(ImageIO.read(frame.toFile()), x, y, null);
                g.setColor(new Color(0, 0, 0, 190));
                g.fillRect(x, y, 58, 40);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
                g.drawString(String.valueOf(segment.tile()), x + 8, y + 30);
                g.setStroke(new BasicStroke(2));
                g.drawRect(x, y, TILE_W - 1, TILE_H - 1);
            }
            g.dispose();
            Path out = dir.resolve("sheet_" + (sheets.size() + 1) + ".png");
            ImageIO.write(sheet, "png", out.toFile());
            sheets.add(out);
        }
        return sheets;
    }

    private static String narration(List<Scene> scenes) {
        String text = scenes.stream().map(Scene::narration).filter(n -> n != null)
                .collect(Collectors.joining(" "));
        return text.length() > 2500 ? text.substring(0, 2500) : text;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
