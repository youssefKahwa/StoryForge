package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.edl.TransitionPlanner;
import com.storyforge.orchestrator.artifact.RunOverrides;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.edl.EdlBuilder;
import com.storyforge.orchestrator.host.HostPlanner;
import com.storyforge.orchestrator.host.SubscribeNudge;
import com.storyforge.orchestrator.model.Overlay;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.LoudnessStats;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.sound.MusicLibrary;
import com.storyforge.orchestrator.sound.AmbiencePlanner;
import com.storyforge.orchestrator.sound.SfxLibrary;
import com.storyforge.orchestrator.sound.SoundBank;
import com.storyforge.orchestrator.sound.SoundDesigner;
import com.storyforge.orchestrator.sound.SoundMixer;
import com.storyforge.orchestrator.sound.SoundPlan;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AudioMixStage implements StageWorker {

    public static final String MIX = "mix.wav";
    public static final String PLAN = "sound_plan.json";
    /** Owner revision "no music at all" is stored as a music gain at or below this. */
    public static final double MUSIC_OFF_DB = -40;
    private static final int SAMPLE_RATE = 48_000;
    /** Loudness normalisation overshoots the true-peak target by up to ~0.8 dB (run 132: -0.73 for -1.5). */
    private static final double PEAK_MARGIN_DB = 1.2;
    private static final Logger log = LoggerFactory.getLogger(AudioMixStage.class);

    /** One library sound layered on a transition: its category, level against the transition's, and whether it
     *  builds up to the cut (risers, reverse cymbals) instead of hitting on it. */
    private record Layer(String category, double gainOffset) {
    }

    private static final Map<String, List<Layer>> LAYERS = Map.of(
            "film_burn", List.of(new Layer("whoosh", 0), new Layer("boom", -5)),
            "zoom_in", List.of(new Layer("whoosh", 0), new Layer("impact", -7)),
            "whip", List.of(new Layer("whoosh_fast", 0)),
            "whoosh", List.of(new Layer("swoosh_soft", 0)),
            "swoosh_soft", List.of(new Layer("swoosh_soft", 0)),
            "glitch", List.of(new Layer("glitch", 0)),
            "flash_hit", List.of(new Layer("impact", 0)),
            "boom", List.of(new Layer("boom", 0), new Layer("riser", -5)),
            "sub_drop", List.of(new Layer("sub_drop", 0), new Layer("reverse", -4)),
            "shutter", List.of(new Layer("shutter", 0)));
    /** Synthesized presets peak at about -3 dBFS; library sounds are levelled to the same reference. */
    private static final double PRESET_PEAK_DB = -3;
    private static final double AMBIENCE_MEAN_DB = -40;
    private static final double CUE_PEAK_DB = -17;
    /** Sounds the owner picked by ear (Freesound ids, CC0): "FX - Swoosh - Slow", "Mouse Click",
     *  "Reward Notification 3_1", "ppop". */
    private static final long SWOOSH_ID = 475133;
    private static final long CLICK_ID = 825193;
    private static final long CHIME_ID = 504784;
    private static final long POP_ID = 527522;
    /** Chapter card on screen (LongForm CHAPTER_FRAMES at 30 fps). */
    private static final double CHAPTER_SECONDS = 3.5;

    private final FfmpegService ffmpeg;
    private final MusicLibrary music;
    private final SfxLibrary sfx;
    private final SoundBank bank;
    private final SoundMixer mixer;
    private final StoryForgeProperties.Qa qa;
    private final boolean soundEnabled;
    private final PreferencesService preferences;
    private final SubscribeNudge subscribe;
    private final com.storyforge.orchestrator.host.HostMascot mascot;

    public AudioMixStage(FfmpegService ffmpeg, MusicLibrary music, SfxLibrary sfx, SoundMixer mixer,
                         StoryForgeProperties properties, PreferencesService preferences, SoundBank bank,
                         SubscribeNudge subscribe, com.storyforge.orchestrator.host.HostMascot mascot) {
        this.mascot = mascot;
        this.subscribe = subscribe;
        this.bank = bank;
        this.preferences = preferences;
        this.ffmpeg = ffmpeg;
        this.music = music;
        this.sfx = sfx;
        this.mixer = mixer;
        this.qa = properties.qa();
        this.soundEnabled = properties.sound().enabled();
    }

    /** An older media-svc may not know a newer preset; the transition then simply plays without sound. */
    private boolean sfxAvailable(String preset) {
        try {
            sfx.file(preset);
            return true;
        } catch (RuntimeException e) {
            log.warn("Transition sound '{}' unavailable ({}); restart StoryForge to update media-svc", preset,
                    e.getMessage());
            return false;
        }
    }

    @Override
    public Stage stage() {
        return Stage.AUDIO_MIX;
    }

    @Override
    public List<String> outputs() {
        return List.of(MIX, "audio_qa.json");
    }

    @Override
    public void execute(StageContext context) {
        var files = context.files();
        NarrationManifest narration = files.read(Stage.NARRATE, "narration.json", NarrationManifest.class);
        Path voice = files.path(Stage.NARRATE, NarrateStage.NARRATION);
        Path mix = files.path(Stage.AUDIO_MIX, MIX);
        List<AudioQa.Track> tracks = new ArrayList<>(List.of(new AudioQa.Track("narration", narration.file(),
                "Narration", LicenseRecord.generated(narration.provider()))));
        Path loudnessInput = voice;
        List<double[]> bubbles = bubblePlan(files);
        files.write(Stage.AUDIO_MIX, HostPlanner.PLAN, new HostPlanner.Plan(bubbles));
        List<Double> subscribeAt = subscribePlan(files, WavAudio.durationSec(voice), bubbles);
        files.write(Stage.AUDIO_MIX, SubscribeNudge.PLAN, new SubscribeNudge.Plan(subscribeAt));
        if (soundEnabled) {
            List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
            Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
            Outline outline = files.read(Stage.OUTLINE, "outline.json", Outline.class);
            RunOverrides overrides = files.overrides();
            scenes = scenes.stream().map(scene -> overrides.sceneMusic().containsKey(scene.id())
                    ? scene.withMusic(new Scene.MusicCue(overrides.sceneMusic().get(scene.id()),
                            scene.music() != null ? scene.music().intensity() : 0.5))
                    : scene).toList();
            double musicOffset = preferences.get().musicGainOffsetDb() + overrides.musicGainDb();
            if (overrides.musicGainDb() <= MUSIC_OFF_DB) {
                musicOffset = Double.NEGATIVE_INFINITY;
            }
            Map<String, List<MusicLibrary.Track>> library = Double.isInfinite(musicOffset) ? Map.of() : music.scan();
            List<Edl.EdlChapter> chapters = EdlBuilder.chapters(scenes, alignment, outline);
            List<SoundPlan.SfxCue> transitionCues = new ArrayList<>();
            if (files.exists(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST)) {
                ShotList shotList = files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class);
                List<TransitionPlanner.Transition> transitions = TransitionPlanner.plan(shotList, scenes, chapters,
                        alignment);
                Set<String> maps = files.exists(Stage.VISUAL_PLAN, VisualPlanStage.MAPS)
                        ? files.read(Stage.VISUAL_PLAN, VisualPlanStage.MAPS,
                                new TypeReference<Map<String, Object>>() { }).keySet()
                        : Set.of();
                for (TransitionPlanner.Transition t : transitions) {
                    if (t.sfx() == null) {
                        continue;
                    }
                    String reason = "transition " + t.kind() + " " + t.shotId();
                    List<SoundPlan.SfxCue> layered = layered(t.sfx(), t.at(), t.gainDb(), t.shotId(), reason);
                    if (!layered.isEmpty()) {
                        transitionCues.addAll(layered);
                    } else if (sfxAvailable(t.sfx())) {
                        transitionCues.add(new SoundPlan.SfxCue(t.sfx(), sfx.file(t.sfx()),
                                Math.max(0, Math.round((t.at() - t.lead()) * 1000) / 1000.0), t.gainDb(), reason));
                    }
                }
                for (TransitionPlanner.Punch p : TransitionPlanner.punches(shotList, maps, transitions)) {
                    if (p.sfx() == null) {
                        continue;
                    }
                    String reason = "punch-in " + p.shotId();
                    List<SoundPlan.SfxCue> layered = layered(p.sfx(), p.at(), p.gainDb(), p.shotId(), reason);
                    if (!layered.isEmpty()) {
                        transitionCues.addAll(layered);
                    } else if (sfxAvailable(p.sfx())) {
                        transitionCues.add(new SoundPlan.SfxCue(p.sfx(), sfx.file(p.sfx()), p.at(), p.gainDb(),
                                reason));
                    }
                }
            }
            long runSeed = context.run().id();
            SoundPlan designed = SoundDesigner.plan(scenes, alignment, chapters, library, runSeed, sfx::file,
                    Double.isInfinite(musicOffset) ? 0 : musicOffset, transitionCues,
                    name -> bank.cue(name, runSeed).map(s -> new SoundDesigner.CueSound(s.file(),
                            s.gainForPeak(CUE_PEAK_DB), s.peak())).orElse(null));
            List<SoundPlan.SfxCue> all = new ArrayList<>(designed.sfx());
            all.addAll(ambience(scenes, alignment, runSeed));
            all.addAll(hostFoley(files, runSeed));
            all.addAll(subscribeSounds(subscribeAt, runSeed));
            for (double[] bubble : bubbles) {
                addSound(all, "pop", POP_ID, bubble[0] + 0.12, -21, "talking logo pops in");
            }
            all.sort((a, b) -> Double.compare(a.start(), b.start()));
            SoundPlan plan = new SoundPlan(designed.music(), all);
            if (plan.music().isEmpty()) {
                log.warn("Run {}: no music in {}, mixing voice and SFX only", context.run().id(), music.root());
            }
            files.write(Stage.AUDIO_MIX, PLAN, plan);
            loudnessInput = files.path(Stage.AUDIO_MIX, "premix.wav");
            mixer.mix(voice, plan, loudnessInput);
            tracks.addAll(soundTracks(plan, library, bank));
            context.usage().provider("sound-designer");
        }
        LoudnessStats stats = ffmpeg.normalizeLoudness(loudnessInput, mix, qa.loudnessTarget(),
                qa.truePeakMax() - PEAK_MARGIN_DB, SAMPLE_RATE);
        boolean passed = Math.abs(stats.integratedLufs() - qa.loudnessTarget()) <= qa.loudnessTolerance()
                && stats.truePeakDb() <= qa.truePeakMax();
        context.usage().provider("ffmpeg-loudness");
        files.write(Stage.AUDIO_MIX, "audio_qa.json", new AudioQa(stats.integratedLufs(), stats.truePeakDb(),
                WavAudio.durationSec(mix), passed, tracks));
    }

    /**
     * Recorded library sounds for one transition, each levelled and aligned so its loudest moment lands on the cut
     * (a riser's loudest moment is its end). Empty when the bank has nothing, so the synthesized preset plays.
     */
    private List<SoundPlan.SfxCue> layered(String preset, double at, double gainDb, String shotId, String reason) {
        List<Layer> layers = LAYERS.get(preset);
        List<SoundPlan.SfxCue> cues = new ArrayList<>();
        if (layers == null) {
            return cues;
        }
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            Optional<SoundBank.Sound> sound = bank.pick(layer.category(), (shotId + layer.category()).hashCode());
            if (sound.isEmpty()) {
                if (i == 0) {
                    return cues;
                }
                continue;
            }
            SoundBank.Sound s = sound.get();
            double start = Math.max(0, Math.round((at - s.peak()) * 1000) / 1000.0);
            cues.add(new SoundPlan.SfxCue(layer.category(), s.file(), start,
                    s.gainForPeak(gainDb + PRESET_PEAK_DB + layer.gainOffset()), reason + " / " + layer.category()));
        }
        return cues;
    }

    /**
     * When the subscribe pop-up appears (see {@link SubscribeNudge}): clear of the masked host, full-screen text,
     * chapter titles, other overlays and the talking logo. Empty when switched off or the logo is missing.
     */
    /**
     * When the talking logo pops in (questions to the viewer, the opening, the outro); in a video with the masked
     * character only where it does not cover him. Empty when switched off.
     */
    private List<double[]> bubblePlan(com.storyforge.orchestrator.artifact.RunFiles files) {
        if (!preferences.get().hostEnabled() || !mascot.available() && !subscribe.available()) {
            return List.of();
        }
        try {
            Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
            return HostPlanner.clearOf(HostPlanner.appearances(alignment.words(), alignment.durationSec()),
                    characterSpans(files));
        } catch (RuntimeException e) {
            log.warn("Talking logo skipped: {}", e.getMessage());
            return List.of();
        }
    }

    /** Where the masked character is on screen. */
    private static List<double[]> characterSpans(com.storyforge.orchestrator.artifact.RunFiles files) {
        if (!files.exists(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST) || !files.exists(Stage.ASSETS, "assets.json")) {
            return List.of();
        }
        Set<String> host = new java.util.HashSet<>(files.read(Stage.ASSETS, "assets.json", AssetManifest.class)
                .assets().stream().filter(a -> "host".equals(a.media())).map(AssetManifest.Asset::shotId).toList());
        return files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class).shots().stream()
                .filter(s -> host.contains(s.id())).map(s -> new double[] {s.start(), s.end()}).toList();
    }

    private List<Double> subscribePlan(com.storyforge.orchestrator.artifact.RunFiles files, double duration,
                                       List<double[]> bubbles) {
        if (!subscribe.available()) {
            return List.of();
        }
        try {
            List<double[]> busy = new ArrayList<>();
            Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
            busy.addAll(bubbles);
            busy.addAll(characterSpans(files));
            if (files.exists(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST)) {
                files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class).shots().stream()
                        .filter(ShotList.Shot::graphic).forEach(s -> busy.add(new double[] {s.start(), s.end()}));
            }
            if (files.exists(Stage.VISUAL_PLAN, VisualPlanStage.OVERLAYS)) {
                files.read(Stage.VISUAL_PLAN, VisualPlanStage.OVERLAYS, new TypeReference<List<Overlay>>() { })
                        .forEach(o -> busy.add(new double[] {o.start(), o.end()}));
            }
            List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
            Outline outline = files.read(Stage.OUTLINE, "outline.json", Outline.class);
            EdlBuilder.chapters(scenes, alignment, outline).stream().skip(1)
                    .forEach(c -> busy.add(new double[] {c.start(), c.start() + CHAPTER_SECONDS}));
            return SubscribeNudge.plan(duration, busy);
        } catch (RuntimeException e) {
            log.warn("Subscribe pop-up skipped: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The pop-up's sounds, each aligned so its loudest moment lands on the animation: a soft swoosh as it slides
     * in, a mouse click on the button, the bell ding, a quieter swoosh as it leaves. The click falls back to the
     * synthesized shutter when the bank has no click.
     */
    private List<SoundPlan.SfxCue> subscribeSounds(List<Double> starts, long seed) {
        List<SoundPlan.SfxCue> cues = new ArrayList<>();
        for (double t : starts) {
            String reason = "subscribe pop-up " + t;
            // The owner's picks: a soft swoosh in, a real mouse click, a light chime, a quieter swoosh out.
            addSound(cues, "whoosh_fast", SWOOSH_ID, t + SubscribeNudge.ENTER_AT, -21, reason);
            if (!addSound(cues, "click", CLICK_ID, t + SubscribeNudge.CLICK_AT, -15, reason) && sfxAvailable("shutter")) {
                cues.add(new SoundPlan.SfxCue("shutter", sfx.file("shutter"), t + SubscribeNudge.CLICK_AT, -16, reason));
            }
            addSound(cues, "bell", CHIME_ID, t + SubscribeNudge.BELL_AT, -18, reason);
            addSound(cues, "whoosh_fast", SWOOSH_ID, t + SubscribeNudge.EXIT_AT, -27, reason);
        }
        return cues;
    }

    /** A specific bank sound by its Freesound id (see {@link SoundBank#find}). */
    private boolean addSound(List<SoundPlan.SfxCue> cues, String category, long id, double at, double peakDb,
                             String reason) {
        Optional<SoundBank.Sound> sound = bank.find(category, id);
        sound.ifPresent(s -> cues.add(new SoundPlan.SfxCue(category, s.file(),
                Math.max(0, Math.round((at - s.peak()) * 1000) / 1000.0), s.gainForPeak(peakDb),
                reason + " / " + category)));
        return sound.isPresent();
    }

    /** A soft chair or cloth sound when the masked host appears, as if the camera cut to a real person. */
    private List<SoundPlan.SfxCue> hostFoley(com.storyforge.orchestrator.artifact.RunFiles files, long seed) {
        List<SoundPlan.SfxCue> cues = new ArrayList<>();
        if (!files.exists(Stage.ASSETS, "assets.json") || !files.exists(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST)) {
            return cues;
        }
        Map<String, Double> starts = new LinkedHashMap<>();
        files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class).shots()
                .forEach(shot -> starts.put(shot.id(), shot.start()));
        for (AssetManifest.Asset asset : files.read(Stage.ASSETS, "assets.json", AssetManifest.class).assets()) {
            Double start = starts.get(asset.shotId());
            if (!"host".equals(asset.media()) || start == null) {
                continue;
            }
            bank.pick("chair", seed + asset.shotId().hashCode()).ifPresent(s -> cues.add(new SoundPlan.SfxCue(
                    "chair", s.file(), Math.max(0, start - 0.05), s.gainForPeak(-28), "host " + asset.shotId())));
        }
        return cues;
    }

    /** A quiet bed of room tone, city, rain or crowd under every scene, levelled to the same average loudness. */
    private List<SoundPlan.SfxCue> ambience(List<Scene> scenes, Alignment alignment, long seed) {
        Map<String, Alignment.SceneSpan> spans = new LinkedHashMap<>();
        alignment.scenes().forEach(span -> spans.put(span.sceneId(), span));
        List<SoundPlan.SfxCue> beds = new ArrayList<>();
        for (AmbiencePlanner.Bed bed : AmbiencePlanner.plan(scenes, spans)) {
            Optional<SoundBank.Sound> sound = bank.pick(bed.category(), seed + bed.category().hashCode())
                    .or(() -> bank.pick(AmbiencePlanner.DEFAULT, seed));
            sound.ifPresent(s -> beds.add(new SoundPlan.SfxCue("ambience", s.file(),
                    Math.round(bed.start() * 1000) / 1000.0, s.gainForMean(AMBIENCE_MEAN_DB),
                    "ambience " + s.category(), bed.length())));
        }
        return beds;
    }

    private static List<AudioQa.Track> soundTracks(SoundPlan plan, Map<String, List<MusicLibrary.Track>> library,
                                                   SoundBank bank) {
        Map<String, AudioQa.Track> unique = new LinkedHashMap<>();
        Map<String, MusicLibrary.Track> byFile = new LinkedHashMap<>();
        library.values().forEach(list -> list.forEach(track -> byFile.put(track.file().toString(), track)));
        plan.music().forEach(segment -> {
            MusicLibrary.Track track = byFile.get(segment.file());
            unique.putIfAbsent(segment.file(), new AudioQa.Track("music", segment.track(), segment.track(),
                    track == null ? null : track.license()));
        });
        plan.sfx().forEach(cue -> unique.putIfAbsent(cue.file(), new AudioQa.Track("sfx",
                Path.of(cue.file()).getFileName().toString(), cue.preset(),
                bank.license(cue.file()).orElse(SfxLibrary.LICENSE))));
        return new ArrayList<>(unique.values());
    }
}
