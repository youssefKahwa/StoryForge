package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.inspiration.Inspiration;
import com.storyforge.orchestrator.inspiration.InspirationFiles;
import com.storyforge.orchestrator.inspiration.InspirationService;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.clips.FootageIndex.Segment;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Choice;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Need;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Part;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Pick;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
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
 *       {@code clip_work} searches), plus any copy the owner put in {@code library/films}. {@link FootageIndex} groups
 *       the copies of each moment, so the same footage is never counted twice.</li>
 *   <li>Catalogue: one copy of every moment is shown to Claude on numbered contact sheets and described once
 *       ({@code clip_describe}); the catalogue is cached per work, so the next video on it costs nothing.</li>
 *   <li>Matching: one text call ({@code clip_match}) gives each narration shot up to three ranked moments.</li>
 *   <li>Assignment: {@link FootagePlanner} takes the best pick that respects the limit on repeats. A shot left empty
 *       goes on with the previous scene, then gets one more matching pass; only what is still empty moves on to real
 *       media and AI pictures.</li>
 *   <li>Cutting: each clip plays its moments for the whole shot, muted, full screen, credited, with a light grade.</li>
 * </ol>
 * Never done: mirroring, pitch or speed tricks to dodge Content ID. The protection is the commentary itself.
 */
@Component
public class FilmClipFinder {

    public record Work(String work, Integer year, List<String> queries) {
    }

    /** picks: up to three ranked moments. An answer in the older form (tile, more) still reads. */
    public record Match(String shot, Integer tile, List<Integer> more, List<Pick> picks) {
        List<Pick> ranked() {
            if (picks != null && !picks.isEmpty()) {
                return picks;
            }
            return tile == null ? List.of() : List.of(new Pick(tile, more));
        }
    }

    public record Matches(List<Match> matches) {
    }

    /**
     * moments: the footage moments on screen, for the report on repeats; cuts: video times of the cuts inside;
     * creatorChoice: one of its moments is a moment the owner's main video showed.
     */
    public record Clip(String shotId, Path file, LicenseRecord license, String credit, String label,
                       double sourceSeconds, List<Integer> moments, List<Double> cuts, boolean creatorChoice) {
    }

    public record Described(Integer tile, String scene, Boolean text) {
    }

    public record Descriptions(List<Described> tiles) {
    }

    record CatalogEntry(String key, String scene, boolean text) {
    }

    private static final Logger log = LoggerFactory.getLogger(FilmClipFinder.class);
    private static final Pattern BLACK = Pattern.compile("black_duration:([0-9.]+)");
    private static final Pattern VIDEO_FILE = Pattern.compile("(?i).+\\.(mp4|mkv|webm|mov|avi)");
    /** Longer shots stay on one picture for too long even as a chain of moments. */
    static final double MAX_SHOT = 14;
    private static final int MAX_QUERIES = 8;
    private static final int VIDEOS_PER_QUERY = 2;
    private static final double LIBRARY_SECONDS = 45 * 60;
    private static final int MIN_LIBRARY_VIDEOS = 6;
    /** Less different footage than this share of the video: search wider once, before repeating anything. */
    private static final double TOP_UP_BELOW = 0.9;
    /** More than this share of the video without a pick: one more matching pass, on the moments not used yet. */
    private static final double EXTRA_PASS_ABOVE = 0.05;
    /** A clip that was rejected tries the next pick, a few times at most. */
    private static final int MAX_ATTEMPTS = 6;
    /** The library holds less than this share of the main video's footage: search wider once. */
    private static final double CREATOR_COVERAGE_TOP_UP = 0.5;
    private static final int SHEETS_PER_CALL = 6;
    private static final int TILE_W = 480;
    private static final int TILE_H = 270;
    private static final int COLS = 3;
    private static final int ROWS = 3;

    private final LlmRouter llm;
    private final MediaSvcClient media;
    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final boolean enabled;
    private final Path library;
    private final Path films;
    private final Path claudeWork;
    private final FootageIndex index;
    private final int maxUses;
    private final CreatorShots creator;
    private final InspirationService inspirations;
    private final InspirationFiles inspirationFiles;

    public FilmClipFinder(LlmRouter llm, MediaSvcClient media, FfmpegService ffmpeg, JsonMapper json,
                          StoryForgeProperties properties, InspirationService inspirations,
                          InspirationFiles inspirationFiles,
                          @Value("${storyforge.clips.enabled:false}") boolean enabled,
                          @Value("${storyforge.clips.max-uses:2}") int maxUses) {
        this.index = new FootageIndex(ffmpeg, json);
        this.maxUses = maxUses;
        this.inspirations = inspirations;
        this.inspirationFiles = inspirationFiles;
        this.creator = new CreatorShots(ffmpeg, json, media, index, llm,
                Path.of(properties.dataDir()).toAbsolutePath().resolve("inspirations").resolve("videos"),
                Path.of(properties.llm().claudeCode().workDir()).toAbsolutePath());
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

    /** How many times one moment of footage may appear in a video. */
    public int maxUses() {
        return maxUses;
    }

    public Map<String, Clip> find(StageContext context, ShotList shots, Alignment alignment, List<Scene> scenes,
                                  Set<String> excluded, int width, int height, double share, List<Double> beats) {
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
            List<MediaSvcClient.Trailer> sources = sources(work, dir, false);
            List<Segment> segments = index.segments(sources, dir);
            if (segments.isEmpty()) {
                return Map.of();
            }
            Map<Integer, CatalogEntry> moments = catalog(context, work, segments, dir);
            Optional<CreatorShots.MainVideo> main = mainVideo(context);
            CreatorShots.Timeline timeline = main.isPresent() ? creatorTimeline(context, work, main.get(), segments, dir)
                    : null;
            boolean fewMoments = differentFootage(segments, moments) < shots.durationSec() * TOP_UP_BELOW;
            boolean fewOfTheCreators = timeline != null && timeline.coverage() < CREATOR_COVERAGE_TOP_UP;
            if (fewMoments || fewOfTheCreators) {
                List<MediaSvcClient.Trailer> wider = sources(work, dir, true);
                if (wider.size() > sources.size()) {
                    segments = index.segments(wider, dir);
                    moments = catalog(context, work, segments, dir);
                    if (main.isPresent()) {
                        timeline = creatorTimeline(context, work, main.get(), segments, dir);
                    }
                }
            }
            Map<Integer, CatalogEntry> described = moments;
            List<Segment> usable = segments.stream().filter(s -> {
                CatalogEntry entry = described.get(s.moment());
                return entry != null && !entry.text();
            }).toList();
            List<Shot> candidates = shots.shots().stream()
                    .filter(s -> !s.graphic() && !excluded.contains(s.id()) && s.duration() <= MAX_SHOT)
                    .sorted(Comparator.comparingDouble(Shot::start)).toList();
            if (candidates.isEmpty() || usable.isEmpty()) {
                return Map.of();
            }
            String creatorLines = creatorLines(timeline, main, usable);
            Set<Integer> creatorMoments = timeline == null ? Set.of() : timeline.parts().stream()
                    .map(CreatorShots.Part::moment).filter(m -> m != null).collect(Collectors.toSet());
            Map<String, List<Pick>> ranked = match(context, work, candidates, usable, described, alignment,
                    creatorLines);
            FootagePlanner planner = new FootagePlanner(usable, maxUses, beats);
            Placing placing = new Placing(context, work, dir, planner, shots.durationSec() * share, width, height,
                    creatorMoments);
            List<Shot> gaps = new ArrayList<>();
            for (Shot shot : candidates) {
                List<Pick> picks = ranked.getOrDefault(shot.id(), List.of());
                if (!placing.place(shot, () -> planner.choose(need(shot), picks))) {
                    gaps.add(shot);
                }
            }
            int matched = placing.clips.size();
            gaps.removeIf(shot -> placing.place(shot, () -> planner.continuation(need(shot))));
            int continued = placing.clips.size() - matched;
            double empty = gaps.stream().mapToDouble(Shot::duration).sum();
            if (empty > shots.durationSec() * EXTRA_PASS_ABOVE) {
                extraPass(context, work, gaps, usable, described, alignment, placing, creatorLines);
            }
            log.info("Run {}: footage on {} shot(s), {} s of {} s ({} matched, {} continued, {} from a second pass); "
                    + "{} shot(s) left for other pictures", context.run().id(), placing.clips.size(),
                    fmt(placing.total), fmt(shots.durationSec()), matched, continued,
                    placing.clips.size() - matched - continued, gaps.size());
            return placing.clips;
        } catch (IOException | RuntimeException e) {
            log.warn("Run {}: film footage skipped: {}", context.run().id(), e.getMessage());
            return Map.of();
        }
    }

    private static Need need(Shot shot) {
        return new Need(shot.id(), shot.start(), shot.end());
    }

    /** The run's main inspiration video with a timed transcript (fetched again once when it has no times). */
    private Optional<CreatorShots.MainVideo> mainVideo(StageContext context) {
        Long id = context.run().inspirationId();
        if (id == null) {
            return Optional.empty();
        }
        try {
            Inspiration inspiration = inspirations.get(id);
            String url = inspiration.getMainUrl();
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            List<VideoTranscript> all = new ArrayList<>(inspirationFiles.transcripts(id));
            VideoTranscript transcript = all.stream().filter(t -> url.equals(t.url())).findFirst().orElse(null);
            if (transcript == null || !transcript.timed()) {
                VideoTranscript fresh = media.transcript(url);
                all.removeIf(t -> url.equals(t.url()));
                all.add(0, fresh);
                inspirationFiles.saveTranscripts(id, all);
                transcript = fresh;
            }
            return Optional.of(new CreatorShots.MainVideo(url, transcript));
        } catch (RuntimeException e) {
            log.warn("Run {}: main video not read: {}", context.run().id(), e.getMessage());
            return Optional.empty();
        }
    }

    private CreatorShots.Timeline creatorTimeline(StageContext context, Work work, CreatorShots.MainVideo main,
                                                  List<Segment> segments, Path dir) {
        try {
            return creator.timeline(context, label(work), main, segments, dir);
        } catch (IOException | RuntimeException e) {
            log.warn("Run {}: main video's shots not read: {}", context.run().id(), e.getMessage());
            return null;
        }
    }

    /** The main video for clip_match, with catalogue numbers for the moments the library holds. */
    private static String creatorLines(CreatorShots.Timeline timeline, Optional<CreatorShots.MainVideo> main,
                                       List<Segment> usable) {
        if (timeline == null || main.isEmpty()) {
            return "(none)";
        }
        Map<Integer, Integer> tileOf = new HashMap<>();
        usable.stream().filter(Segment::best).forEach(s -> tileOf.put(s.moment(), s.tile()));
        String lines = CreatorShots.lines(timeline, main.get().transcript().segments(), tileOf::get);
        return lines.isBlank() ? "(none)" : lines;
    }

    /** Seconds of footage that are truly different: one copy of each moment that is not a title card. */
    private static double differentFootage(List<Segment> segments, Map<Integer, CatalogEntry> moments) {
        return segments.stream().filter(Segment::best).filter(s -> {
            CatalogEntry entry = moments.get(s.moment());
            return entry != null && !entry.text();
        }).mapToDouble(Segment::length).sum();
    }

    /** The shots still empty, matched again against only the moments not on screen yet. */
    private void extraPass(StageContext context, Work work, List<Shot> gaps, List<Segment> usable,
                           Map<Integer, CatalogEntry> moments, Alignment alignment, Placing placing,
                           String creatorLines) {
        List<Segment> unused = usable.stream().filter(s -> !placing.planner.used(s.moment())).toList();
        if (unused.isEmpty()) {
            return;
        }
        try {
            Map<String, List<Pick>> ranked = match(context, work, gaps, unused, moments, alignment, creatorLines);
            gaps.removeIf(shot -> placing.place(shot, () -> placing.planner.choose(need(shot),
                    ranked.getOrDefault(shot.id(), List.of()))));
        } catch (RuntimeException e) {
            log.warn("Run {}: second footage pass skipped: {}", context.run().id(), e.getMessage());
        }
    }

    /** One text call: up to three ranked moments for every shot. */
    private Map<String, List<Pick>> match(StageContext context, Work work, List<Shot> shots, List<Segment> usable,
                                          Map<Integer, CatalogEntry> moments, Alignment alignment,
                                          String creatorLines) {
        String catalogLines = usable.stream().filter(Segment::best).map(s -> s.tile() + " | " + fmt1(s.length())
                + " s | " + tags(s) + " | " + moments.get(s.moment()).scene()).collect(Collectors.joining("\n"));
        String shotLines = shots.stream().map(s -> s.id() + " | " + fmt1(s.duration()) + " s | "
                + RevisionService.spoken(alignment, s)).collect(Collectors.joining("\n"));
        Matches matches = llm.json(context, Stage.ASSETS, "clip_match", Map.of(
                "work", label(work),
                "catalog", catalogLines,
                "creator", creatorLines,
                "shots", shotLines), Matches.class);
        Map<String, List<Pick>> ranked = new HashMap<>();
        for (Match match : matches.matches() == null ? List.<Match>of() : matches.matches()) {
            if (match.shot() != null) {
                ranked.put(match.shot(), match.ranked());
            }
        }
        return ranked;
    }

    private static String tags(Segment segment) {
        if (segment.clean()) {
            return "-";
        }
        return segment.dark() && segment.soft() ? "dark, soft" : segment.dark() ? "dark" : "soft";
    }

    /** One video's cutting: tries the planner's choices for a shot until one renders properly. */
    private final class Placing {
        private final StageContext context;
        private final Work work;
        private final Path dir;
        private final FootagePlanner planner;
        private final double limit;
        private final int width;
        private final int height;
        private final Map<String, Clip> clips = new HashMap<>();
        private final Set<Integer> creatorMoments;
        private double total;

        Placing(StageContext context, Work work, Path dir, FootagePlanner planner, double limit, int width,
                int height, Set<Integer> creatorMoments) {
            this.creatorMoments = creatorMoments;
            this.context = context;
            this.work = work;
            this.dir = dir;
            this.planner = planner;
            this.limit = limit;
            this.width = width;
            this.height = height;
        }

        boolean place(Shot shot, Supplier<Optional<Choice>> chooser) {
            if (total + shot.duration() > limit) {
                return false;
            }
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                Optional<Choice> choice = chooser.get();
                if (choice.isEmpty()) {
                    return false;
                }
                Clip clip = render(context, work, dir, shot, choice.get(), width, height, creatorMoments);
                if (clip == null) {
                    // A fade-to-black or a night shot too dark to read: the next pick is better than any AI picture.
                    log.info("Run {} {}: footage unusable, trying the next pick", context.run().id(), shot.id());
                    planner.reject(choice.get());
                    continue;
                }
                planner.commit(choice.get());
                clips.put(shot.id(), clip);
                total += shot.duration();
                return true;
            }
            return false;
        }
    }

    /** Renders a clip to exactly the shot's length, or returns null if it is too dark to show or cannot be cut. */
    private Clip render(StageContext context, Work work, Path dir, Shot shot, Choice choice, int width, int height,
                        Set<Integer> creatorMoments) {
        double target = choice.need().target();
        double source = choice.seconds();
        double factor = Math.max(FootagePlanner.SLOWEST, source / target);
        double hold = Math.max(0, target - source / factor);
        Path out = context.files().path(Stage.ASSETS, shot.id() + ".clip.mp4");
        List<String> args = new ArrayList<>(List.of("-y", "-loglevel", "error"));
        StringBuilder graph = new StringBuilder();
        int inputs = 0;
        for (Part part : choice.parts()) {
            Segment segment = part.segment();
            args.addAll(List.of("-ss", fmt(part.from()), "-t", fmt(part.seconds()), "-i",
                    segment.source().file().toString()));
            graph.append(f("[%d:v]", inputs)).append(index.bars(segment.source(), dir))
                    .append(watermark(segment.source())).append("hqdn3d=1.5:1.5:4:4,scale=").append(width).append(':')
                    .append(height).append(":force_original_aspect_ratio=increase,crop=").append(width).append(':')
                    .append(height).append(',').append(look(segment.brightness()))
                    .append("fps=30,setsar=1,setpts=PTS-STARTPTS").append(f("[m%d];", inputs));
            inputs++;
        }
        for (int i = 0; i < inputs; i++) {
            graph.append(f("[m%d]", i));
        }
        graph.append(f("concat=n=%d:v=1:a=0,setpts=PTS/%s,tpad=stop_mode=clone:stop_duration=%s[v]", inputs,
                fmt(factor), fmt(hold + 0.1)));
        args.addAll(List.of("-filter_complex", graph.toString(), "-map", "[v]", "-map_metadata", "-1",
                "-write_tmcd", "0", "-an", "-dn", "-t", fmt(target), "-c:v", "libx264", "-preset", "veryfast",
                "-crf", "20", "-pix_fmt", "yuv420p", out.toString()));
        try {
            ffmpeg.ffmpeg(args);
        } catch (RuntimeException e) {
            log.warn("Run {} {}: footage could not be cut: {}", context.run().id(), shot.id(), e.getMessage());
            return null;
        }
        if (mostlyBlack(out, target)) {
            try {
                Files.deleteIfExists(out);
            } catch (IOException e) {
                log.debug("Could not delete {}", out);
            }
            return null;
        }
        MediaSvcClient.Trailer from = choice.parts().get(0).segment().source();
        Set<MediaSvcClient.Trailer> used = new LinkedHashSet<>();
        choice.parts().forEach(part -> used.add(part.segment().source()));
        String where = used.stream().map(t -> t.url() == null || t.url().isBlank() ? "owner's copy of the work" : t.url())
                .distinct().collect(Collectors.joining(", "));
        String titles = used.stream().map(t -> "\"" + t.title() + "\" (" + t.channel() + ")")
                .collect(Collectors.joining(", "));
        String evidence = "Footage from " + titles + ", " + fmt(source)
                + " s shown without audio under original commentary and criticism (fair use): " + where;
        return new Clip(shot.id(), out, new LicenseRecord("fair_use_clip", "Footage: " + from.channel(), from.url(),
                evidence, null), label(work), from.title(), source, choice.moments(),
                factor >= 1 ? FootagePlanner.cuts(shot.start(), choice.parts()) : List.of(),
                choice.moments().stream().anyMatch(creatorMoments::contains));
    }

    /**
     * A light grade so 1990s and 2000s footage sits well next to today's videos: dark moments are lifted a little
     * (the darker, the more, within limits), then a touch of contrast, colour and sharpness.
     */
    static String look(double brightness) {
        double gamma = Math.min(1.32, Math.max(1, Math.pow(46 / Math.max(brightness, 10), 0.33)));
        return f("eq=gamma=%.2f:contrast=1.04:saturation=1.08,unsharp=5:5:0.55:5:5:0,", gamma);
    }

    /**
     * The footage library of a work: reused when it is big enough, topped up from YouTube otherwise. {@code wider}
     * searches again even so, for a video that needs more different footage than the library holds.
     */
    private List<MediaSvcClient.Trailer> sources(Work work, Path dir, boolean wider) throws IOException {
        Files.createDirectories(dir);
        Path index = dir.resolve("sources.json");
        List<MediaSvcClient.Trailer> known = Files.isRegularFile(index)
                ? new ArrayList<>(json.readValue(index.toFile(), new TypeReference<List<MediaSvcClient.Trailer>>() { })
                        .stream().filter(t -> Files.isRegularFile(t.file())).toList())
                : new ArrayList<>();
        double seconds = known.stream().mapToDouble(MediaSvcClient.Trailer::durationSec).sum();
        double limit = wider ? LIBRARY_SECONDS * 2 : LIBRARY_SECONDS;
        if (wider || known.size() < MIN_LIBRARY_VIDEOS) {
            for (String query : work.queries().stream().limit(MAX_QUERIES).toList()) {
                if (seconds >= limit) {
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
     * What every moment shows, described by Claude from contact sheets (one copy each; the others inherit the line).
     * Cached per work. catalog.json holds the older, less exact lines: only a fallback when a description fails.
     */
    private Map<Integer, CatalogEntry> catalog(StageContext context, Work work, List<Segment> segments, Path dir)
            throws IOException {
        Path file = dir.resolve("catalog2.json");
        Map<String, CatalogEntry> known = entries(file);
        Map<Integer, List<Segment>> copies = FootageIndex.byMoment(segments);
        List<Segment> missing = segments.stream().filter(Segment::best)
                .filter(s -> copies.get(s.moment()).stream().noneMatch(c -> known.containsKey(c.key()))).toList();
        if (!missing.isEmpty()) {
            Path sheetDir = claudeWork.resolve("clips").resolve(slug(work.work()));
            int perCall = SHEETS_PER_CALL * COLS * ROWS;
            for (int from = 0; from < missing.size(); from += perCall) {
                List<Segment> batch = missing.subList(from, Math.min(missing.size(), from + perCall));
                List<Path> sheets = sheets(batch, sheetDir, dir);
                try {
                    Descriptions described = llm.json(context, Stage.ASSETS, "clip_describe", Map.of(
                            "work", label(work),
                            "sheets", sheets.stream().map(Path::toString).collect(Collectors.joining("\n"))),
                            Descriptions.class);
                    Map<Integer, Segment> byTile = batch.stream().collect(Collectors.toMap(Segment::tile, s -> s));
                    for (Described d : described.tiles() == null ? List.<Described>of() : described.tiles()) {
                        Segment segment = d.tile() == null ? null : byTile.get(d.tile());
                        if (segment != null && d.scene() != null && !d.scene().isBlank()) {
                            for (Segment copy : copies.get(segment.moment())) {
                                known.put(copy.key(), new CatalogEntry(copy.key(), d.scene().strip(),
                                        Boolean.TRUE.equals(d.text())));
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    log.warn("Run {}: footage description batch failed: {}", context.run().id(), e.getMessage());
                }
            }
            json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(known.values()));
            log.info("Footage catalogue for {}: {} new moment(s) described", work.work(), missing.size());
        }
        Map<String, CatalogEntry> older = entries(dir.resolve("catalog.json"));
        Map<Integer, CatalogEntry> moments = new HashMap<>();
        copies.forEach((moment, list) -> list.stream().map(c -> known.get(c.key())).filter(e -> e != null).findFirst()
                .or(() -> list.stream().map(c -> older.get(c.key())).filter(e -> e != null).findFirst())
                .ifPresent(entry -> moments.put(moment, entry)));
        return moments;
    }

    private Map<String, CatalogEntry> entries(Path file) throws IOException {
        Map<String, CatalogEntry> entries = new LinkedHashMap<>();
        if (Files.isRegularFile(file)) {
            json.readValue(file.toFile(), new TypeReference<List<CatalogEntry>>() { })
                    .forEach(entry -> entries.put(entry.key(), entry));
        }
        return entries;
    }

    /** Numbered contact sheets (3x3 big tiles) of each moment's middle frame, for the model to look at. */
    private List<Path> sheets(List<Segment> segments, Path dir, Path library) throws IOException {
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
                        index.bars(segment.source(), library) + "scale=" + TILE_W + ":" + TILE_H
                                + ":force_original_aspect_ratio=increase,crop=" + TILE_W + ":" + TILE_H,
                        frame.toString()));
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

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }

    private static String fmt1(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
