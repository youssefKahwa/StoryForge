package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.alignment.AlignmentProvider;
import com.storyforge.orchestrator.provider.tts.TtsProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class NarrateStage implements StageWorker {

    public static final String NARRATION = "narration.wav";
    public static final String CLIP_ALIGNMENTS = "clip_alignments.json";

    private static final Logger log = LoggerFactory.getLogger(NarrateStage.class);
    private static final double FIT_THRESHOLD = 0.04;
    private static final double MIN_SPEED = 0.88;
    private static final double MAX_SPEED = 1.12;

    private final TtsProvider tts;
    private final AlignmentProvider aligner;
    private final StoryForgeProperties.Narration narration;

    public NarrateStage(TtsProvider tts, AlignmentProvider aligner, StoryForgeProperties properties) {
        this.tts = tts;
        this.aligner = aligner;
        this.narration = properties.narration();
    }

    @Override
    public Stage stage() {
        return Stage.NARRATE;
    }

    @Override
    public List<String> outputs() {
        return List.of(NARRATION, "narration.json");
    }

    private record Take(WavAudio.Builder audio, List<SceneAudio> clips, Map<String, ClipAlignment> alignments,
                        double silenceSec) {
    }

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        context.usage().provider(tts.name());
        double target = context.run().targetSeconds();
        double speedFactor = 1.0;
        Take take = render(context, scenes, speedFactor);
        double total = take.audio().durationSec();
        if (Math.abs(total - target) / target > FIT_THRESHOLD) {
            double speech = total - take.silenceSec();
            speedFactor = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speech / Math.max(1, target - take.silenceSec())));
            log.info("Run {} narration is {}s for a {}s target, re-synthesizing at {}x", context.run().id(),
                    Math.round(total), Math.round(target), Math.round(speedFactor * 100) / 100.0);
            take = render(context, scenes, speedFactor);
        }
        Path output = context.files().path(Stage.NARRATE, NARRATION);
        take.audio().write(output);
        if (take.alignments().size() == scenes.size()) {
            context.files().write(Stage.NARRATE, CLIP_ALIGNMENTS, take.alignments());
        }
        context.files().write(Stage.NARRATE, "narration.json", new NarrationManifest(tts.name(), tts.voice(),
                round(take.audio().durationSec()), context.files().relative(output), take.clips(),
                Math.round(speedFactor * 1000) / 1000.0));
    }

    private Take render(StageContext context, List<Scene> scenes, double speedFactor) {
        WavAudio.Builder merged = WavAudio.builder();
        List<SceneAudio> clips = new ArrayList<>();
        Map<String, ClipAlignment> alignments = new LinkedHashMap<>();
        double silence = 0;
        for (Scene scene : scenes) {
            String name = "scene_" + scene.id() + ".wav";
            Path clip = context.files().path(Stage.NARRATE, name);
            synthesizeVerified(context, scene, clip, speedFactor)
                    .ifPresent(aligned -> alignments.put(scene.id(), aligned));
            double offset = merged.durationSec();
            merged.silence(scene.silenceBeforeMs() / 1000.0).append(clip).silence(scene.silenceAfterMs() / 1000.0);
            silence += (scene.silenceBeforeMs() + scene.silenceAfterMs()) / 1000.0;
            clips.add(new SceneAudio(scene.id(), Stage.NARRATE.folder() + "/" + name, round(offset),
                    round(merged.durationSec() - offset), scene.silenceBeforeMs(), scene.silenceAfterMs()));
        }
        return new Take(merged, clips, alignments, silence);
    }

    private Optional<ClipAlignment> synthesizeVerified(StageContext context, Scene scene, Path clip,
                                                       double speedFactor) {
        String text = String.join(" ", scene.words());
        tts.synthesize(scene, clip, speedFactor);
        Optional<ClipAlignment> first = aligner.alignClip(clip, text);
        if (first.isEmpty() || first.get().wer() <= narration.maxWer()) {
            return first;
        }
        context.usage().provider(aligner.name());
        log.info("Run {} scene {}: WER {} above {}, re-synthesizing slower", context.run().id(), scene.id(),
                first.get().wer(), narration.maxWer());
        Path retry = clip.resolveSibling("retry_" + clip.getFileName());
        tts.synthesize(scene, retry, speedFactor * narration.retrySpeedFactor());
        Optional<ClipAlignment> second = aligner.alignClip(retry, text);
        try {
            if (second.isPresent() && second.get().wer() < first.get().wer()) {
                Files.move(retry, clip, StandardCopyOption.REPLACE_EXISTING);
                return second;
            }
            Files.deleteIfExists(retry);
            return first;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
