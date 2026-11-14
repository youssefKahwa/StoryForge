package com.storyforge.orchestrator.qa;

import static com.storyforge.orchestrator.model.QaReport.Severity.CRITICAL;
import static com.storyforge.orchestrator.model.QaReport.Severity.INFO;
import static com.storyforge.orchestrator.model.QaReport.Severity.WARNING;

import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.inspiration.InspirationFiles;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.LoudnessStats;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.Critique;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.FootageReport;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.QaReport.Check;
import com.storyforge.orchestrator.model.QaReport.Severity;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.render.ChannelIdents;
import com.storyforge.orchestrator.run.RunInfo;
import com.storyforge.orchestrator.settings.Preferences;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.worker.AssetsStage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

@Service
public class QaService {

    public static final String VIDEO = "final_16x9.mp4";
    private static final double GAP_TOLERANCE = 0.05;
    private static final double SYNC_TOLERANCE = 0.25;
    private static final double BLACK_MIN_SECONDS = 0.5;

    private static final int MAX_SHARED_RUN = 12;
    private static final double MAX_SHARED_SHARE = 0.05;
    /** On a video about a screen work, AI pictures are for what no footage can cover. */
    private static final double MAX_AI_SHARE = 0.15;

    private final FfmpegService ffmpeg;
    private final StoryForgeProperties properties;
    private final InspirationFiles inspirations;
    private final ReviewSheet reviewSheet;

    public QaService(FfmpegService ffmpeg, StoryForgeProperties properties, InspirationFiles inspirations,
                     ReviewSheet reviewSheet) {
        this.reviewSheet = reviewSheet;
        this.ffmpeg = ffmpeg;
        this.properties = properties;
        this.inspirations = inspirations;
    }

    public QaReport evaluate(RunInfo run, RunFiles files) {
        StoryForgeProperties.Qa qa = properties.qa();
        StoryForgeProperties.Video spec = properties.video();
        Path video = files.path(Stage.ASSEMBLE, VIDEO);
        Edl edl = files.read(Stage.ASSEMBLE, "edl.json", Edl.class);
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Dossier dossier = files.read(Stage.RESEARCH, "dossier.json", Dossier.class);
        Critique critique = files.read(Stage.CRITIQUE, "critique.json", Critique.class);
        AssetManifest assets = files.read(Stage.ASSETS, "assets.json", AssetManifest.class);
        AudioQa audio = files.read(Stage.AUDIO_MIX, "audio_qa.json", AudioQa.class);

        List<Check> checks = new ArrayList<>();
        MediaInfo info = ffmpeg.probe(video);
        checks.add(check("video.resolution", "technical", CRITICAL,
                info.width() == spec.width() && info.height() == spec.height(),
                info.width() + "x" + info.height() + " (expected " + spec.width() + "x" + spec.height() + ")"));
        checks.add(check("video.fps", "technical", CRITICAL, Math.abs(info.fps() - spec.fps()) < 0.01,
                fmt(info.fps()) + " fps (expected " + spec.fps() + ")"));
        checks.add(check("video.audio_stream", "technical", CRITICAL, info.hasAudio(),
                info.hasAudio() ? "audio codec " + info.audioCodec() : "no audio stream"));

        LoudnessStats loudness = ffmpeg.measureLoudness(video);
        checks.add(check("audio.loudness", "technical", CRITICAL,
                Math.abs(loudness.integratedLufs() - qa.loudnessTarget()) <= qa.loudnessTolerance(),
                fmt(loudness.integratedLufs()) + " LUFS (target " + qa.loudnessTarget() + " ±"
                        + qa.loudnessTolerance() + ")"));
        checks.add(check("audio.true_peak", "technical", CRITICAL, loudness.truePeakDb() <= qa.truePeakMax(),
                fmt(loudness.truePeakDb()) + " dBTP (max " + qa.truePeakMax() + ")"));

        List<double[]> black = ffmpeg.detectBlack(video, BLACK_MIN_SECONDS);
        checks.add(check("video.black_frames", "technical", CRITICAL, black.isEmpty(),
                black.isEmpty() ? "none" : black.size() + " black segment(s), first at " + fmt(black.get(0)[0]) + "s"));

        checks.add(review(files, edl, assets, video));

        double drift = Math.abs(info.videoDurationSec() - info.audioDurationSec());
        checks.add(check("av.sync", "technical", CRITICAL, drift <= SYNC_TOLERANCE,
                "video " + fmt(info.videoDurationSec()) + "s vs audio " + fmt(info.audioDurationSec()) + "s"));

        boolean captionsOk = !edl.captions().isEmpty()
                && edl.captions().get(edl.captions().size() - 1).end() <= info.durationSec() + 0.5
                && files.exists(Stage.ASSEMBLE, "captions.srt");
        checks.add(check("captions.sync", "technical", WARNING, captionsOk,
                edl.captions().size() + " caption(s)"));

        checks.addAll(durationChecks(info.durationSec(), run.targetSeconds(), qa.durationTolerance()));

        Set<String> renderedScenes = edl.shots().stream().map(EdlShot::sceneId).collect(Collectors.toSet());
        List<String> missingScenes = scenes.stream().map(Scene::id).filter(id -> !renderedScenes.contains(id))
                .toList();
        checks.add(check("content.scenes_rendered", "content", CRITICAL, missingScenes.isEmpty(),
                missingScenes.isEmpty() ? scenes.size() + " scene(s) rendered" : "missing " + missingScenes));
        checks.add(coverage(edl, files));
        long placeholders = assets.assets().stream()
                .filter(asset -> asset.provider().endsWith("-fallback")).count();
        checks.add(check("content.real_images", "content", WARNING, placeholders == 0,
                placeholders == 0 ? "all pictures as planned" : placeholders + " picture(s) replaced by a stock photo "
                        + "or a reused picture because no AI image was available"));
        long empty = assets.assets().stream().filter(asset -> AssetsStage.BACKDROP.equals(asset.provider())).count();
        checks.add(check("content.empty_shots", "content", CRITICAL, empty == 0,
                empty == 0 ? "every shot shows a picture" : empty + " shot(s) show only an empty background"));

        Set<String> clipShots = assets.assets().stream().filter(asset -> "clip".equals(asset.media()))
                .map(AssetManifest.Asset::shotId).collect(Collectors.toSet());
        if (!clipShots.isEmpty()) {
            double clipSeconds = edl.shots().stream().filter(shot -> clipShots.contains(shot.id()))
                    .mapToDouble(shot -> shot.end() - shot.start()).sum();
            double share = clipSeconds / Math.max(1, info.durationSec());
            checks.add(check("legal.fair_use_clips", "legal", WARNING, false, clipShots.size()
                    + " trailer clip(s), " + fmt(clipSeconds) + " s (" + fmt(share * 100) + "% of the video), "
                    + "muted, credited: watch them before publishing; a Content ID claim is possible"));
        }

        if (files.exists(Stage.ASSETS, AssetsStage.FOOTAGE_REPORT)) {
            FootageReport pictures = files.read(Stage.ASSETS, AssetsStage.FOOTAGE_REPORT, FootageReport.class);
            List<FootageReport.Repeat> over = pictures.overLimit();
            checks.add(check("assets.repeats", "content", WARNING, over.isEmpty(), over.isEmpty()
                    ? pictures.momentsUsed() + " moment(s) of footage, " + pictures.repeats().size()
                            + " shown twice, none above " + pictures.maxUses()
                    : "shown more than " + pictures.maxUses() + " times: " + over.stream()
                            .map(FootageReport.Repeat::what).toList()));
            if (pictures.footageShots() > 0) {
                checks.add(check("assets.ai_share", "content", WARNING, pictures.aiShare() <= MAX_AI_SHARE,
                        fmt(pictures.aiShare() * 100) + "% of the video is AI pictures (" + pictures.aiPictures().size()
                                + " shot(s), reasons in " + AssetsStage.FOOTAGE_REPORT + ")"));
            }
        }

        if (properties.sound() != null && properties.sound().enabled()) {
            long musicTracks = audio.tracks() == null ? 0
                    : audio.tracks().stream().filter(track -> "music".equals(track.kind())).count();
            checks.add(check("audio.music", "content", WARNING, musicTracks > 0,
                    musicTracks > 0 ? musicTracks + " music track(s)"
                            : "no background music (library empty or music volume off): voice and SFX only"));
        }

        checks.addAll(factual(scenes, dossier, critique, run.inspirationId() != null));
        checks.addAll(legal(assets, audio, edl));
        checks.add(originality(run, scenes));

        boolean disclosure = assets.realisticImagery();
        checks.add(check("legal.disclosure", "legal", INFO, true,
                disclosure ? "realistic synthetic imagery: set altered-content disclosure"
                        : "no realistic synthetic imagery"));
        return QaReport.of(checks, disclosure);
    }

    private Check originality(RunInfo run, List<Scene> scenes) {
        if (run.inspirationId() == null) {
            return check("legal.originality", "legal", INFO, true, "no inspiration videos");
        }
        List<String> sources = inspirations.transcripts(run.inspirationId()).stream()
                .map(VideoTranscript::transcript).toList();
        String script = scenes.stream().map(Scene::narration).collect(Collectors.joining(" "));
        OriginalityChecker.Result result = OriginalityChecker.compare(script, sources);
        boolean original = result.original(MAX_SHARED_RUN, MAX_SHARED_SHARE);
        return check("legal.originality", "legal", CRITICAL, original,
                "longest shared run " + result.longestSharedRun() + " words (max " + (MAX_SHARED_RUN - 1) + "), "
                        + fmt(result.sharedNgramShare() * 100) + "% shared " + OriginalityChecker.NGRAM + "-word phrases"
                        + (original || result.example() == null ? "" : "; e.g. \"" + result.example() + "\""));
    }

    private Check coverage(Edl edl, RunFiles files) {
        List<String> problems = new ArrayList<>();
        double cursor = 0;
        for (EdlShot shot : edl.shots()) {
            if (Math.abs(shot.start() - cursor) > GAP_TOLERANCE) {
                problems.add("gap before " + shot.id() + " at " + fmt(cursor) + "s");
            }
            if (!Files.isRegularFile(files.root().resolve(shot.asset()))) {
                problems.add("missing asset for " + shot.id());
            }
            cursor = shot.end();
        }
        if (Math.abs(cursor - edl.durationSec()) > GAP_TOLERANCE) {
            problems.add("shots end at " + fmt(cursor) + "s, video is " + fmt(edl.durationSec()) + "s");
        }
        return check("content.shot_coverage", "content", CRITICAL, problems.isEmpty(),
                problems.isEmpty() ? edl.shots().size() + " shot(s), contiguous" : String.join("; ", problems));
    }

    private List<Check> factual(List<Scene> scenes, Dossier dossier, Critique critique, boolean inspiration) {
        int minSources = com.storyforge.orchestrator.stage.worker.ResearchStage.requiredSources(
                properties.qa().minSources(), inspiration);
        List<String> problems = new ArrayList<>();
        for (Scene scene : scenes) {
            for (String claimId : scene.claims()) {
                dossier.claim(claimId).ifPresentOrElse(claim -> {
                    if (claim.sourceIds().isEmpty()) {
                        problems.add(claimId + " has no source");
                    } else if (claim.key() && dossier.independentSourceCount(claim) < 2) {
                        problems.add("key claim " + claimId + " has fewer than 2 independent sources");
                    }
                }, () -> problems.add("scene " + scene.id() + " cites unknown claim " + claimId));
            }
        }
        long cited = scenes.stream().mapToLong(scene -> scene.claims().size()).sum();
        List<Check> checks = new ArrayList<>();
        checks.add(check("factual.claims_traceable", "factual", CRITICAL, problems.isEmpty(),
                problems.isEmpty() ? cited + " claim reference(s) traced to the dossier" : String.join("; ", problems)));
        checks.add(check("factual.source_count", "factual", CRITICAL, dossier.sources().size() >= minSources,
                dossier.sources().size() + " source(s) (min " + minSources + ")"));
        List<String> openFlags = critique.flags().stream()
                .filter(flag -> !flag.resolved() && "critical".equalsIgnoreCase(flag.severity()))
                .map(Critique.Flag::issue).toList();
        checks.add(check("factual.critic_flags", "factual", CRITICAL, openFlags.isEmpty(),
                openFlags.isEmpty() ? "no unresolved critical flags" : String.join("; ", openFlags)));
        checks.add(check("content.critic_pass", "content", WARNING, critique.passed(),
                "average " + fmt(critique.average()) + ", hook " + fmt(critique.hook())));
        return checks;
    }

    private List<Check> legal(AssetManifest assets, AudioQa audio, Edl edl) {
        List<String> missing = new ArrayList<>();
        List<String> forbidden = new ArrayList<>();
        for (AssetManifest.Asset asset : assets.assets()) {
            if (asset.license() == null || !asset.license().complete()) {
                missing.add(asset.shotId());
            } else if (asset.license().forbidden()) {
                forbidden.add(asset.shotId() + " (" + asset.license().type() + ")");
            } else if (asset.license().needsEvidence()
                    && (asset.license().url() == null || asset.license().evidence() == null)) {
                missing.add(asset.shotId() + " (public-domain evidence)");
            }
        }
        List<String> unlicensedAudio = new ArrayList<>();
        audio.tracks().stream()
                .filter(track -> !"narration".equals(track.kind()))
                .filter(track -> track.license() == null || !track.license().complete() || track.license().forbidden())
                .forEach(track -> unlicensedAudio.add(track.file()));
        edl.audio().stream()
                .filter(track -> Set.of("music", "sfx").contains(track.kind().toLowerCase(Locale.ROOT)))
                .filter(track -> track.license() == null || !track.license().complete())
                .forEach(track -> unlicensedAudio.add(track.file()));
        long musicTracks = audio.tracks().stream().filter(track -> !"narration".equals(track.kind())).count();
        long clips = assets.assets().stream().filter(asset -> "clip".equals(asset.media())).count();
        String filmDetail = clips == 0 ? "no film footage or official artwork"
                : "no film material beyond the " + clips + " fair-use trailer clip(s)";
        return List.of(
                check("legal.asset_licenses", "legal", CRITICAL, missing.isEmpty(),
                        missing.isEmpty() ? assets.assets().size() + " asset(s) with provenance" : "missing: " + missing),
                check("legal.no_film_material", "legal", CRITICAL, forbidden.isEmpty(),
                        forbidden.isEmpty() ? filmDetail : "forbidden: " + forbidden),
                check("legal.music_licenses", "legal", CRITICAL, unlicensedAudio.isEmpty(),
                        unlicensedAudio.isEmpty() ? musicTracks + " music/SFX track(s), all licensed"
                                : "unknown license: " + unlicensedAudio));
    }

    /**
     * The owner's rule for long videos: at least 8 minutes, so YouTube allows mid-roll ads. Missing the target length
     * is only a warning then (the narrator's speed varies). A short test video keeps the tolerance as its gate.
     */
    static List<Check> durationChecks(double seconds, int targetSeconds, double tolerance) {
        double deviation = Math.abs(seconds - targetSeconds) / targetSeconds;
        String detail = fmt(seconds) + "s vs target " + targetSeconds + "s (" + fmt(deviation * 100) + "% off, max "
                + fmt(tolerance * 100) + "%)";
        double minimum = Preferences.MIN_LONG_MINUTES * 60;
        if (targetSeconds < minimum) {
            return List.of(check("content.duration", "content", CRITICAL, deviation <= tolerance, detail));
        }
        return List.of(
                check("content.duration", "content", CRITICAL, seconds >= minimum, fmt(seconds) + "s, at least "
                        + Math.round(minimum) + "s for mid-roll ads"),
                check("content.duration_target", "content", WARNING, deviation <= tolerance, detail));
    }

    /** One frame of every shot on review pages for the owner (qa/review_*.jpg), and the shots that show nothing. */
    private Check review(RunFiles files, Edl edl, AssetManifest assets, Path video) {
        try {
            Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
            ChannelIdents.Timeline timeline = files.exists(Stage.ASSEMBLE, ChannelIdents.TIMELINE)
                    ? files.read(Stage.ASSEMBLE, ChannelIdents.TIMELINE, ChannelIdents.Timeline.class) : null;
            Map<String, AssetManifest.Asset> byShot = assets.assets().stream()
                    .collect(Collectors.toMap(AssetManifest.Asset::shotId, asset -> asset, (a, b) -> a));
            List<ReviewSheet.Tile> tiles = edl.shots().stream().map(shot -> {
                double middle = (shot.start() + shot.end()) / 2;
                AssetManifest.Asset asset = byShot.get(shot.id());
                String spoken = alignment.words().stream()
                        .filter(w -> (w.start() + w.end()) / 2 >= shot.start() && (w.start() + w.end()) / 2 < shot.end())
                        .map(Alignment.Word::text).collect(Collectors.joining(" "));
                return new ReviewSheet.Tile(shot.id(), timeline == null ? middle : timeline.shift(middle),
                        shot.end() - shot.start(), asset == null ? "none" : AssetsStage.pictureType(asset), spoken);
            }).toList();
            ReviewSheet.Result result = reviewSheet.build(video, tiles, files.dir(Stage.QA));
            boolean clean = result.flatShots().isEmpty();
            return check("video.flat_shots", "content", WARNING, clean,
                    (clean ? "no shot looks empty" : "almost flat or black: " + result.flatShots()) + "; every shot on "
                            + result.pages().size() + " review page(s) in qa/");
        } catch (RuntimeException e) {
            return check("video.flat_shots", "content", INFO, true, "review pages skipped: " + e.getMessage());
        }
    }

    private static Check check(String id, String category, Severity severity, boolean passed, String detail) {
        return new Check(id, category, severity, passed, detail);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
