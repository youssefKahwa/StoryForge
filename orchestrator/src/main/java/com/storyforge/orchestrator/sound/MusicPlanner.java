package com.storyforge.orchestrator.sound;

import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.artifact.RunOverrides;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.sound.SoundPlan.MusicSegment;
import com.storyforge.orchestrator.stage.Stage;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Which music plays where, known before the mix: the scenes' moods with the owner's music revisions, the library, and
 * the run's seed give the same tracks every time. AUDIO_MIX plays this plan; the visual plan and the footage cut on
 * its beat.
 */
@Component
public class MusicPlanner {

    /** Owner revision "no music at all" is stored as a music gain at or below this. */
    public static final double MUSIC_OFF_DB = -40;

    /** What the music plan is made from; musicOffsetDb is negative infinity when the owner switched music off. */
    public record Inputs(List<Scene> scenes, Alignment alignment, Map<String, List<MusicLibrary.Track>> library,
                         double musicOffsetDb) {
        public boolean musicOff() {
            return Double.isInfinite(musicOffsetDb);
        }
    }

    private final MusicLibrary music;
    private final PreferencesService preferences;
    private final boolean soundEnabled;

    public MusicPlanner(MusicLibrary music, PreferencesService preferences, StoryForgeProperties properties) {
        this.music = music;
        this.preferences = preferences;
        this.soundEnabled = properties.sound() != null && properties.sound().enabled();
    }

    public Inputs inputs(RunFiles files) {
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        RunOverrides overrides = files.overrides();
        scenes = scenes.stream().map(scene -> overrides.sceneMusic().containsKey(scene.id())
                ? scene.withMusic(new Scene.MusicCue(overrides.sceneMusic().get(scene.id()),
                        scene.music() != null ? scene.music().intensity() : 0.5))
                : scene).toList();
        double offset = preferences.get().musicGainOffsetDb() + overrides.musicGainDb();
        if (overrides.musicGainDb() <= MUSIC_OFF_DB) {
            offset = Double.NEGATIVE_INFINITY;
        }
        return new Inputs(scenes, alignment, Double.isInfinite(offset) ? Map.of() : music.scan(), offset);
    }

    /** The music segments AUDIO_MIX will play for this run; empty when sound or music is off. */
    public List<MusicSegment> segments(RunFiles files, long runId) {
        if (!soundEnabled) {
            return List.of();
        }
        Inputs in = inputs(files);
        if (in.musicOff()) {
            return List.of();
        }
        Map<String, SceneSpan> spans = in.alignment().scenes().stream()
                .collect(Collectors.toMap(SceneSpan::sceneId, Function.identity(), (a, b) -> a));
        return SoundDesigner.music(in.scenes(), spans, in.alignment().durationSec(), in.library(), runId,
                in.musicOffsetDb());
    }
}
