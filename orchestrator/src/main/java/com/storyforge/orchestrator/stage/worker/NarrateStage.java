package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.tts.TtsProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class NarrateStage implements StageWorker {

    public static final String NARRATION = "narration.wav";

    private final TtsProvider tts;

    public NarrateStage(TtsProvider tts) {
        this.tts = tts;
    }

    @Override
    public Stage stage() {
        return Stage.NARRATE;
    }

    @Override
    public List<String> outputs() {
        return List.of(NARRATION, "narration.json");
    }

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        WavAudio.Builder merged = WavAudio.builder();
        List<SceneAudio> clips = new ArrayList<>();
        context.usage().provider(tts.name());
        for (Scene scene : scenes) {
            String name = "scene_" + scene.id() + ".wav";
            Path clip = context.files().path(Stage.NARRATE, name);
            tts.synthesize(scene, clip);
            double offset = merged.durationSec();
            merged.silence(scene.silenceBeforeMs() / 1000.0).append(clip).silence(scene.silenceAfterMs() / 1000.0);
            clips.add(new SceneAudio(scene.id(), Stage.NARRATE.folder() + "/" + name, round(offset),
                    round(merged.durationSec() - offset), scene.silenceBeforeMs(), scene.silenceAfterMs()));
        }
        Path narration = context.files().path(Stage.NARRATE, NARRATION);
        merged.write(narration);
        context.files().write(Stage.NARRATE, "narration.json", new NarrationManifest(tts.name(), tts.voice(),
                round(merged.durationSec()), context.files().relative(narration), clips));
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
