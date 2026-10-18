package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.ClipAlignment.ClipWord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.alignment.AlignmentProvider;
import com.storyforge.orchestrator.provider.tts.TtsProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.voice.VoiceCalibration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final double FIT_THRESHOLD = 0.03;
    private static final double MIN_TEMPO = 0.85;
    private static final double MAX_TEMPO = 1.15;

    private record Take(WavAudio.Builder audio, List<SceneAudio> clips, Map<String, ClipAlignment> alignments,
                        double silenceSec) {
    }

    private final TtsProvider tts;
    private final AlignmentProvider aligner;
    private final FfmpegService ffmpeg;
    private final VoiceCalibration calibration;
    private final StoryForgeProperties.Narration narration;

    public NarrateStage(TtsProvider tts, AlignmentProvider aligner, FfmpegService ffmpeg,
                        VoiceCalibration calibration, StoryForgeProperties properties) {
        this.tts = tts;
        this.aligner = aligner;
        this.ffmpeg = ffmpeg;
        this.calibration = calibration;
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

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        String voice = context.run().voice() != null ? context.run().voice() : tts.voice();
        context.usage().provider(tts.name());

        Map<String, ClipAlignment> alignments = new LinkedHashMap<>();
        for (Scene scene : scenes) {
            synthesizeVerified(context, scene, clipPath(context, scene))
                    .ifPresent(aligned -> alignments.put(scene.id(), aligned));
        }
        Take take = assemble(context, scenes, alignments);
        double speech = take.audio().durationSec() - take.silenceSec();
        int words = scenes.stream().mapToInt(scene -> scene.words().size()).sum();
        calibration.record(voice, words, speech);

        double target = context.run().targetSeconds();
        double tempo = 1.0;
        if (Math.abs(take.audio().durationSec() - target) / target > FIT_THRESHOLD) {
            tempo = Math.max(MIN_TEMPO, Math.min(MAX_TEMPO, speech / Math.max(1, target - take.silenceSec())));
            log.info("Run {} narration is {}s for a {}s target, time-stretching at {}x", context.run().id(),
                    Math.round(take.audio().durationSec()), Math.round(target), Math.round(tempo * 100) / 100.0);
            for (Scene scene : scenes) {
                stretch(clipPath(context, scene), tempo);
            }
            Map<String, ClipAlignment> scaled = new LinkedHashMap<>();
            for (Map.Entry<String, ClipAlignment> entry : alignments.entrySet()) {
                scaled.put(entry.getKey(), scale(entry.getValue(), tempo));
            }
            take = assemble(context, scenes, scaled);
        }

        Path output = context.files().path(Stage.NARRATE, NARRATION);
        take.audio().write(output);
        if (take.alignments().size() == scenes.size()) {
            context.files().write(Stage.NARRATE, CLIP_ALIGNMENTS, take.alignments());
        }
        context.files().write(Stage.NARRATE, "narration.json", new NarrationManifest(tts.name(), voice,
                round(take.audio().durationSec()), context.files().relative(output), take.clips(),
                Math.round(tempo * 1000) / 1000.0));
    }

    private Take assemble(StageContext context, List<Scene> scenes, Map<String, ClipAlignment> alignments) {
        WavAudio.Builder merged = WavAudio.builder();
        List<SceneAudio> clips = new ArrayList<>();
        double silence = 0;
        for (Scene scene : scenes) {
            Path clip = clipPath(context, scene);
            double offset = merged.durationSec();
            merged.silence(scene.silenceBeforeMs() / 1000.0).append(clip).silence(scene.silenceAfterMs() / 1000.0);
            silence += (scene.silenceBeforeMs() + scene.silenceAfterMs()) / 1000.0;
            clips.add(new SceneAudio(scene.id(), Stage.NARRATE.folder() + "/" + clip.getFileName(), round(offset),
                    round(merged.durationSec() - offset), scene.silenceBeforeMs(), scene.silenceAfterMs()));
        }
        return new Take(merged, clips, alignments, silence);
    }

    private Path clipPath(StageContext context, Scene scene) {
        return context.files().path(Stage.NARRATE, "scene_" + scene.id() + ".wav");
    }

    private void stretch(Path clip, double tempo) {
        Path stretched = clip.resolveSibling("stretched_" + clip.getFileName());
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", clip.toString(),
                "-filter:a", String.format(Locale.ROOT, "atempo=%.4f", tempo),
                "-ar", String.valueOf(WavAudio.SAMPLE_RATE), "-ac", "1", "-c:a", "pcm_s16le", stretched.toString()));
        try {
            Files.move(stretched, clip, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ClipAlignment scale(ClipAlignment alignment, double tempo) {
        List<ClipWord> words = alignment.words().stream()
                .map(word -> new ClipWord(word.text(), round(word.start() / tempo), round(word.end() / tempo)))
                .toList();
        return new ClipAlignment(words, alignment.transcript(), alignment.wer(), round(alignment.durationSec() / tempo));
    }

    private Optional<ClipAlignment> synthesizeVerified(StageContext context, Scene scene, Path clip) {
        String text = String.join(" ", scene.words());
        tts.synthesize(scene, clip, 1.0, context.run().voice());
        Optional<ClipAlignment> first = aligner.alignClip(clip, text);
        if (first.isEmpty() || first.get().wer() <= narration.maxWer()) {
            return first;
        }
        context.usage().provider(aligner.name());
        log.info("Run {} scene {}: WER {} above {}, re-synthesizing slower", context.run().id(), scene.id(),
                first.get().wer(), narration.maxWer());
        Path retry = clip.resolveSibling("retry_" + clip.getFileName());
        tts.synthesize(scene, retry, narration.retrySpeedFactor(), context.run().voice());
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
