package com.storyforge.orchestrator.qa;

import static com.storyforge.orchestrator.model.QaReport.Severity.CRITICAL;
import static com.storyforge.orchestrator.model.QaReport.Severity.INFO;
import static com.storyforge.orchestrator.model.QaReport.Severity.WARNING;

import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.LoudnessStats;
import com.storyforge.orchestrator.media.MediaInfo;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.Critique;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.QaReport.Check;
import com.storyforge.orchestrator.model.QaReport.Severity;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.run.RunInfo;
import com.storyforge.orchestrator.stage.Stage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final FfmpegService ffmpeg;
    private final StoryForgeProperties properties;

    public QaService(FfmpegService ffmpeg, StoryForgeProperties properties) {
        this.ffmpeg = ffmpeg;
        this.properties = properties;
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

        double drift = Math.abs(info.videoDurationSec() - info.audioDurationSec());
        checks.add(check("av.sync", "technical", CRITICAL, drift <= SYNC_TOLERANCE,
                "video " + fmt(info.videoDurationSec()) + "s vs audio " + fmt(info.audioDurationSec()) + "s"));

        boolean captionsOk = !edl.captions().isEmpty()
                && edl.captions().get(edl.captions().size() - 1).end() <= info.durationSec() + 0.5
                && files.exists(Stage.ASSEMBLE, "captions.srt");
        checks.add(check("captions.sync", "technical", WARNING, captionsOk,
                edl.captions().size() + " caption(s)"));

        double deviation = Math.abs(info.durationSec() - run.targetSeconds()) / run.targetSeconds();
        checks.add(check("content.duration", "content", CRITICAL, deviation <= qa.durationTolerance(),
                fmt(info.durationSec()) + "s vs target " + run.targetSeconds() + "s ("
                        + fmt(deviation * 100) + "% off, max " + fmt(qa.durationTolerance() * 100) + "%)"));

        Set<String> renderedScenes = edl.shots().stream().map(EdlShot::sceneId).collect(Collectors.toSet());
        List<String> missingScenes = scenes.stream().map(Scene::id).filter(id -> !renderedScenes.contains(id))
                .toList();
        checks.add(check("content.scenes_rendered", "content", CRITICAL, missingScenes.isEmpty(),
                missingScenes.isEmpty() ? scenes.size() + " scene(s) rendered" : "missing " + missingScenes));
        checks.add(coverage(edl, files));

        checks.addAll(factual(scenes, dossier, critique));
        checks.addAll(legal(assets, audio, edl));

        boolean disclosure = assets.realisticImagery();
        checks.add(check("legal.disclosure", "legal", INFO, true,
                disclosure ? "realistic synthetic imagery: set altered-content disclosure"
                        : "no realistic synthetic imagery"));
        return QaReport.of(checks, disclosure);
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

    private List<Check> factual(List<Scene> scenes, Dossier dossier, Critique critique) {
        int minSources = properties.qa().minSources();
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
        return List.of(
                check("legal.asset_licenses", "legal", CRITICAL, missing.isEmpty(),
                        missing.isEmpty() ? assets.assets().size() + " asset(s) with provenance" : "missing: " + missing),
                check("legal.no_film_material", "legal", CRITICAL, forbidden.isEmpty(),
                        forbidden.isEmpty() ? "no film footage or official artwork" : "forbidden: " + forbidden),
                check("legal.music_licenses", "legal", CRITICAL, unlicensedAudio.isEmpty(),
                        unlicensedAudio.isEmpty() ? musicTracks + " music/SFX track(s), all licensed"
                                : "unknown license: " + unlicensedAudio));
    }

    private static Check check(String id, String category, Severity severity, boolean passed, String detail) {
        return new Check(id, category, severity, passed, detail);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
