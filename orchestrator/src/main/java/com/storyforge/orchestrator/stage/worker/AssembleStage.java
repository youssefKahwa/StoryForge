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
    private static final int HOST_SIZE = 300;

    private static final int CAPTION_MAX_CHARS = 42;
    private static final double CAPTION_MAX_SECONDS = 3.5;

    private final RenderProvider renderer;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Video video;
    private final PreferencesService preferences;
    private final HostMascot host;
    private final SubscribeNudge subscribe;

    public AssembleStage(RenderProvider renderer, FormatRegistry formats, StoryForgeProperties properties,
                         PreferencesService preferences, HostMascot host, SubscribeNudge subscribe) {
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

    /** Adds the talking channel logo; any problem just leaves it out of this video. */
    private Edl withHost(StageContext context, Edl edl, Alignment alignment) {
        if (!preferences.get().hostEnabled() || !host.available()
                || edl.shots().stream().anyMatch(shot -> "host".equals(shot.media()))) {
            return edl;
        }
        try {
            List<double[]> ranges = HostPlanner.appearances(alignment.words(), alignment.durationSec());
            if (ranges.isEmpty()) {
                return edl;
            }
            var files = context.files();
            HostMascot.Prepared prepared = host.prepare();
            Path dir = files.dir(Stage.ASSEMBLE).resolve(HOST_DIR);
            host.linkInto(prepared, dir);
            double[] loudness = host.loudnessPerFrame(files.path(Stage.NARRATE, NarrateStage.NARRATION), video.fps(),
                    files.dir(Stage.ASSEMBLE));
            List<Edl.HostAppearance> appearances = HostPlanner.mouths(ranges, loudness, prepared.openness(),
                    video.fps());
            return edl.withHost(new Edl.HostTrack(files.relative(dir.resolve(HostMascot.FRAME_PATTERN)),
                    prepared.frameCount(), HOST_SIZE, appearances));
        } catch (RuntimeException e) {
            log.warn("Run {}: talking host skipped: {}", context.run().id(), e.getMessage());
            return edl;
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
        renderer.render(edl, files.root(), files.path(Stage.ASSEMBLE, QaService.VIDEO));
        context.usage().provider(renderer.name());
    }
}
