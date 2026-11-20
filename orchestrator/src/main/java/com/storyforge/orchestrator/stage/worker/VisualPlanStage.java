package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.artifact.RunOverrides;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.MapPin;
import com.storyforge.orchestrator.model.RealMedia;
import com.storyforge.orchestrator.provider.geo.WikidataGeo;
import com.storyforge.orchestrator.sound.BeatGrid;
import com.storyforge.orchestrator.sound.MusicPlanner;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.visual.BeatSnap;
import com.storyforge.orchestrator.visual.ShotDirector;
import com.storyforge.orchestrator.visual.ShotPlanner;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class VisualPlanStage implements StageWorker {

    public static final String SHOTLIST = "shotlist.json";
    public static final String OVERLAYS = "overlays.json";
    public static final String BIBLE = "visual_bible.json";
    public static final String REAL_MEDIA = "real_media.json";
    public static final String MAPS = "maps.json";
    private static final long REVISION_SEED_STEP = 7919;
    private static final Logger log = LoggerFactory.getLogger(VisualPlanStage.class);

    private final FormatRegistry formats;
    private final ShotPlanner planner;
    private final ShotDirector director;
    private final WikidataGeo geo;
    private final MusicPlanner music;
    private final BeatGrid beats;

    private final com.storyforge.orchestrator.visual.MotionDirector motion;

    public VisualPlanStage(FormatRegistry formats, StoryForgeProperties properties, ShotDirector director,
                           WikidataGeo geo, MusicPlanner music, BeatGrid beats,
                           com.storyforge.orchestrator.visual.MotionDirector motion) {
        this.motion = motion;
        this.music = music;
        this.beats = beats;
        this.geo = geo;
        this.formats = formats;
        this.director = director;
        StoryForgeProperties.Shots shots = properties.shots();
        this.planner = new ShotPlanner(new ShotPlanner.Rules(shots.minSeconds(), shots.maxSeconds(),
                shots.maxSameType(), shots.interruptEverySeconds()));
    }

    @Override
    public Stage stage() {
        return Stage.VISUAL_PLAN;
    }

    @Override
    public List<String> outputs() {
        return List.of(SHOTLIST);
    }

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = context.files().read(Stage.ALIGN, "alignment.json", Alignment.class);
        FormatDefinition.Style style = formats.get(context.run().format()).style();
        ShotList plan = planner.plan(scenes, alignment, style, context.run().id() * 10_000L);
        context.usage().provider("shot-planner");
        ShotDirector.Result directed = director.direct(context, context.run().topicTitle(), scenes, alignment, plan,
                style);
        RunOverrides overrides = context.files().overrides();
        ShotList animated = motion.animate(context, scenes, alignment,
                applyRevisions(directed.shots(), overrides, style));
        ShotList shots = onTheBeat(context, animated);
        java.util.Set<String> plain = shots.shots().stream().filter(s -> s.animation() == null).map(Shot::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, RealMedia> real = new HashMap<>(directed.realMedia());
        Map<String, Integer> perScene = new HashMap<>();
        for (Shot shot : shots.shots()) {
            String key = shotKey(shot.sceneId(), perScene.merge(shot.sceneId(), 1, Integer::sum));
            if (overrides.shots().containsKey(key)) {
                real.remove(shot.id());
            }
        }
        Map<String, MapPin> maps = new HashMap<>();
        real.entrySet().removeIf(entry -> {
            if (!RealMedia.MAP.equals(entry.getValue().kind())) {
                return false;
            }
            geo.locate(entry.getValue().query()).ifPresent(pin -> maps.put(entry.getKey(), pin));
            return true;
        });
        context.files().write(Stage.VISUAL_PLAN, REAL_MEDIA, real);
        context.files().write(Stage.VISUAL_PLAN, MAPS, maps);
        context.files().write(Stage.VISUAL_PLAN, BIBLE, directed.bible());
        // An overlay never sits on an animation, nor on a shot an animation took over.
        context.files().write(Stage.VISUAL_PLAN, OVERLAYS, directed.overlays().stream()
                .filter(o -> o.shotId() == null || plain.contains(o.shotId())).toList());
        context.files().write(Stage.VISUAL_PLAN, SHOTLIST, shots);
    }

    /** Cuts inside a scene move onto the music's beat when one is close (the music is known before the mix). */
    private ShotList onTheBeat(StageContext context, ShotList shots) {
        try {
            List<Double> grid = beats.videoBeats(music.segments(context.files(), context.run().id()));
            ShotList snapped = BeatSnap.shots(shots, grid);
            List<Double> cuts = snapped.shots().stream().skip(1).map(Shot::start).toList();
            log.info("Run {}: {} beat(s) in the music, {}% of the shot cuts on the beat", context.run().id(),
                    grid.size(), Math.round(BeatSnap.onBeat(cuts, grid, 0.06) * 100));
            return snapped;
        } catch (RuntimeException e) {
            log.warn("Run {}: shot cuts left off the beat: {}", context.run().id(), e.getMessage());
            return shots;
        }
    }

    /** Owner image revisions: new prompt and a new seed, so only those images are generated again. */
    static ShotList applyRevisions(ShotList shots, RunOverrides overrides, FormatDefinition.Style style) {
        if (overrides.shots().isEmpty()) {
            return shots;
        }
        Map<String, Integer> perScene = new HashMap<>();
        List<Shot> out = new ArrayList<>();
        for (Shot shot : shots.shots()) {
            String key = shotKey(shot.sceneId(), perScene.merge(shot.sceneId(), 1, Integer::sum));
            RunOverrides.ShotOverride override = overrides.shots().get(key);
            if (override == null || shot.graphic()) {
                out.add(shot);
                continue;
            }
            String prompt = override.prompt() == null || override.prompt().isBlank() ? shot.prompt()
                    : style.promptPrefix() + ", " + override.prompt();
            out.add(shot.withPrompt(prompt, shot.seed() + REVISION_SEED_STEP * Math.max(1, override.attempt())));
        }
        return new ShotList(shots.durationSec(), out);
    }

    public static String shotKey(String sceneId, int indexInScene) {
        return sceneId + "#" + indexInScene;
    }
}
