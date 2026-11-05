package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.MapPin;
import java.util.Map;
import com.storyforge.orchestrator.edl.TransitionPlanner;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.edl.CaptionBuilder;
import com.storyforge.orchestrator.edl.EdlBuilder;
import com.storyforge.orchestrator.host.HostMascot;
import com.storyforge.orchestrator.host.HostPlanner;
import com.storyforge.orchestrator.host.SubscribeNudge;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlAudio;
import com.storyforge.orchestrator.model.Edl.EdlCaption;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Overlay;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.provider.render.RenderProvider;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AssembleStage implements StageWorker {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AssembleStage.class);
    private static final String HOST_DIR = "host";
    private static final String HOST_LOGO = "host_logo.jpg";
    /** The rendered video before the intro/outro, and the fingerprint of the edit it was rendered from. */
    private static final String CONTENT = "content_16x9.mp4";
    private static final String CONTENT_EDL = "content_16x9.edl.sha";
    /** The intro goes after the first scene (the hook) when it ends in this window, else at the very start. */
    private static final double INTRO_MIN_AT = 5;
    private static final double INTRO_MAX_AT = 60;
    private static final int HOST_SIZE = 300;

    private static final int CAPTION_MAX_CHARS = 42;
    private static final double CAPTION_MAX_SECONDS = 3.5;

    private final RenderProvider renderer;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Video video;
    private final PreferencesService preferences;
    private final HostMascot host;
    private final SubscribeNudge subscribe;
    private final org.springframework.beans.factory.ObjectProvider<
            com.storyforge.orchestrator.provider.render.ChannelIdents> idents;

    public AssembleStage(RenderProvider renderer, FormatRegistry formats, StoryForgeProperties properties,
                         PreferencesService preferences, HostMascot host, SubscribeNudge subscribe,
                         org.springframework.beans.factory.ObjectProvider<
                                 com.storyforge.orchestrator.provider.render.ChannelIdents> idents) {
        this.idents = idents;
        this.subscribe = subscribe;
        this.host = host;
        this.renderer = renderer;
        this.formats = formats;
        this.video = properties.video();
        this.preferences = preferences;
    }

    @Override
    public Stage stage() {
        return Stage.ASSEMBLE;
    }

    @Override
    public List<String> outputs() {
        return List.of("edl.json", "captions.srt", QaService.VIDEO);
    }

    /**
     * Adds the talking channel logo at the moments the audio mix planned (and gave a pop); in a video with the
     * masked character only where it does not cover him. Any problem just leaves it out of this video.
     */
    private Edl withHost(StageContext context, Edl edl, Alignment alignment) {
        boolean logo = subscribe.logo() != null && java.nio.file.Files.isRegularFile(subscribe.logo());
        if (!preferences.get().hostEnabled() || !logo && !host.available()) {
            return edl;
        }
        try {
            List<double[]> ranges = context.files().exists(Stage.AUDIO_MIX, HostPlanner.PLAN)
                    ? context.files().read(Stage.AUDIO_MIX, HostPlanner.PLAN, HostPlanner.Plan.class).ranges()
                    : HostPlanner.clearOf(HostPlanner.appearances(alignment.words(), alignment.durationSec()),
                            edl.shots().stream().filter(shot -> "host".equals(shot.media()))
                                    .map(shot -> new double[] {shot.start(), shot.end()}).toList());
            if (ranges.isEmpty()) {
                return edl;
            }
            var files = context.files();
            if (logo) {
                // The channel logo with the vector mouth (LogoMouth.jsx), lip-synced to the narration's loudness.
                Path copy = files.path(Stage.ASSEMBLE, HOST_LOGO);
                java.nio.file.Files.copy(subscribe.logo(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                double[] loudness = host.loudnessPerFrame(files.path(Stage.NARRATE, NarrateStage.NARRATION),
                        video.fps(), files.dir(Stage.ASSEMBLE));
                return edl.withHost(new Edl.HostTrack(null, 0, HOST_SIZE,
                        HostPlanner.openness(ranges, loudness, video.fps()), files.relative(copy)));
            }
            HostMascot.Prepared prepared = host.prepare();
            Path dir = files.dir(Stage.ASSEMBLE).resolve(HOST_DIR);
            host.linkInto(prepared, dir);
            double[] loudness = host.loudnessPerFrame(files.path(Stage.NARRATE, NarrateStage.NARRATION), video.fps(),
                    files.dir(Stage.ASSEMBLE));
            List<Edl.HostAppearance> appearances = HostPlanner.mouths(ranges, loudness, prepared.openness(),
                    video.fps());
            return edl.withHost(new Edl.HostTrack(files.relative(dir.resolve(HostMascot.FRAME_PATTERN)),
                    prepared.frameCount(), HOST_SIZE, appearances, null));
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("Run {}: talking host skipped: {}", context.run().id(), e.getMessage());
            return edl;
        }
    }

    /**
     * The channel intro (right after the opening scene, the hook) and the outro (at the end) around the rendered
     * video; the uploaded captions move with them (ident.json tells the package stage how to move the chapters).
     * Any problem leaves the video as rendered.
     */
    private void withIdents(StageContext context, List<EdlCaption> captions, boolean reuse, String fingerprint) {
        var files = context.files();
        Path video = files.path(Stage.ASSEMBLE, QaService.VIDEO);
        Path content = contentCache(files).resolve(CONTENT);
        var channel = idents.getIfAvailable();
        if (channel == null || !channel.available()) {
            if (reuse) {
                copy(content, video);
            }
            return;
        }
        try {
            if (!reuse) {
                java.nio.file.Files.move(video, content, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                java.nio.file.Files.writeString(contentCache(files).resolve(CONTENT_EDL), fingerprint);
            }
            NarrationManifest narration = files.read(Stage.NARRATE, "narration.json", NarrationManifest.class);
            double introAt = 0;
            if (narration.scenes() != null && !narration.scenes().isEmpty()) {
                NarrationManifest.SceneAudio hook = narration.scenes().get(0);
                double end = hook.offsetSec() + hook.durationSec();
                introAt = end >= INTRO_MIN_AT && end <= INTRO_MAX_AT ? end : 0;
            }
            var timeline = channel.wrap(content, video, introAt, narration.voice());
            files.write(Stage.ASSEMBLE, com.storyforge.orchestrator.provider.render.ChannelIdents.TIMELINE, timeline);
            files.writeText(Stage.ASSEMBLE, "captions.srt", CaptionBuilder.toSrt(captions.stream()
                    .map(c -> new EdlCaption(timeline.shift(c.start()), timeline.shift(c.end()), c.text())).toList()));
            log.info("Run {}: intro at {} s ({} s), outro ({} s) added", context.run().id(), timeline.introAt(),
                    timeline.introSeconds(), timeline.outroSeconds());
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("Run {}: intro/outro skipped: {}", context.run().id(), e.getMessage());
            if (java.nio.file.Files.isRegularFile(content)) {
                copy(content, video);
            }
        }
    }

    /** Outside the assemble folder, which a re-run of the stage empties. */
    private static Path contentCache(com.storyforge.orchestrator.artifact.RunFiles files) {
        Path dir = files.root().resolve("render-cache");
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return dir;
    }

    private static String readQuietly(Path file) {
        try {
            return java.nio.file.Files.readString(file).strip();
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static void copy(Path from, Path to) {
        try {
            java.nio.file.Files.copy(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** SHA-256 of the edit (edl.json as written). */
    static String fingerprint(String edlJson) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(edlJson.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The subscribe pop-ups at the times the audio mix planned (and gave their sounds). */
    private Edl withSubscribe(StageContext context, Edl edl) {
        var files = context.files();
        if (!subscribe.available() || !files.exists(Stage.AUDIO_MIX, SubscribeNudge.PLAN)) {
            return edl;
        }
        try {
            List<Double> starts = files.read(Stage.AUDIO_MIX, SubscribeNudge.PLAN, SubscribeNudge.Plan.class).starts();
            if (starts == null || starts.isEmpty()) {
                return edl;
            }
            Path logo = files.path(Stage.ASSEMBLE, SubscribeNudge.LOGO);
            java.nio.file.Files.copy(subscribe.logo(), logo, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            List<Overlay> overlays = new java.util.ArrayList<>(edl.overlays() == null ? List.of() : edl.overlays());
            for (double start : starts) {
                overlays.add(new Overlay(SubscribeNudge.KIND, null, start, start + SubscribeNudge.SECONDS,
                        files.relative(logo), null));
            }
            return edl.withOverlays(overlays);
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("Run {}: subscribe pop-up skipped: {}", context.run().id(), e.getMessage());
            return edl;
        }
    }

    @Override
    public void execute(StageContext context) {
        var files = context.files();
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        ShotList shots = files.read(Stage.VISUAL_PLAN, "shotlist.json", ShotList.class);
        AssetManifest assets = files.read(Stage.ASSETS, "assets.json", AssetManifest.class);
        Outline outline = files.read(Stage.OUTLINE, "outline.json", Outline.class);

        List<EdlCaption> captions = CaptionBuilder.build(alignment.words(), CAPTION_MAX_CHARS, CAPTION_MAX_SECONDS);
        List<EdlAudio> audio = List.of(new EdlAudio("mix",
                files.relative(files.path(Stage.AUDIO_MIX, AudioMixStage.MIX)), 0, 0, null));
        FormatDefinition.Style style = formats.get(context.run().format()).style();
        Edl edl = EdlBuilder.build(new EdlBuilder.Frame(video.width(), video.height(), video.fps()), scenes,
                alignment, shots, assets, outline, audio, captions,
                new Edl.EdlStyle(style.fonts(), style.palette(), preferences.get().burnCaptions()),
                files.exists(Stage.VISUAL_PLAN, VisualPlanStage.OVERLAYS)
                        ? files.read(Stage.VISUAL_PLAN, VisualPlanStage.OVERLAYS, new TypeReference<List<Overlay>>() { })
                        : List.of());
        Map<String, MapPin> maps = files.exists(Stage.VISUAL_PLAN, VisualPlanStage.MAPS)
                ? files.read(Stage.VISUAL_PLAN, VisualPlanStage.MAPS, new TypeReference<Map<String, MapPin>>() { })
                : Map.of();
        List<TransitionPlanner.Transition> plan = TransitionPlanner.plan(shots, scenes, edl.chapters(), alignment);
        Map<String, String> transitions = TransitionPlanner.kinds(plan);
        Map<String, Edl.EdlPunch> punches = new HashMap<>();
        TransitionPlanner.punches(shots, maps.keySet(), plan).forEach(p -> punches.put(p.shotId(),
                new Edl.EdlPunch(p.at(), p.x(), p.y(), p.zoom())));
        edl = edl.withShots(edl.shots().stream().map(shot -> new Edl.EdlShot(shot.id(), shot.sceneId(), shot.type(),
                shot.start(), shot.end(), shot.asset(), shot.motion(), shot.text(),
                transitions.getOrDefault(shot.id(), TransitionPlanner.CUT), shot.media(), shot.credit(),
                maps.get(shot.id()), punches.get(shot.id()))).toList());

        edl = withHost(context, edl, alignment);
        edl = withSubscribe(context, edl);
        files.write(Stage.ASSEMBLE, "edl.json", edl);
        files.writeText(Stage.ASSEMBLE, "captions.srt", CaptionBuilder.toSrt(captions));
        Path timeline = files.path(Stage.ASSEMBLE, com.storyforge.orchestrator.provider.render.ChannelIdents.TIMELINE);
        try {
            java.nio.file.Files.deleteIfExists(timeline);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        // The rendered video before the intro/outro is kept with a fingerprint of its edit: the same edit again
        // (a re-run after an intro/outro problem) skips the long render.
        Path content = contentCache(files).resolve(CONTENT);
        String fingerprint = fingerprint(files.readText(Stage.ASSEMBLE, "edl.json"));
        Path stamp = contentCache(files).resolve(CONTENT_EDL);
        boolean reuse = java.nio.file.Files.isRegularFile(content) && java.nio.file.Files.isRegularFile(stamp)
                && fingerprint.equals(readQuietly(stamp));
        if (reuse) {
            log.info("Run {}: same edit as the last render, reusing it", context.run().id());
        } else {
            renderer.render(edl, files.root(), files.path(Stage.ASSEMBLE, QaService.VIDEO));
        }
        context.usage().provider(renderer.name());
        withIdents(context, captions, reuse, fingerprint);
    }
}
