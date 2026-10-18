package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.LoudnessStats;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AudioMixStage implements StageWorker {

    public static final String MIX = "mix.wav";
    private static final int SAMPLE_RATE = 48_000;
    private static final double PEAK_MARGIN_DB = 0.5;

    private final FfmpegService ffmpeg;
    private final StoryForgeProperties.Qa qa;

    public AudioMixStage(FfmpegService ffmpeg, StoryForgeProperties properties) {
        this.ffmpeg = ffmpeg;
        this.qa = properties.qa();
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
        NarrationManifest narration = context.files().read(Stage.NARRATE, "narration.json", NarrationManifest.class);
        Path input = context.files().path(Stage.NARRATE, NarrateStage.NARRATION);
        Path mix = context.files().path(Stage.AUDIO_MIX, MIX);
        LoudnessStats stats = ffmpeg.normalizeLoudness(input, mix, qa.loudnessTarget(),
                qa.truePeakMax() - PEAK_MARGIN_DB, SAMPLE_RATE);
        boolean passed = Math.abs(stats.integratedLufs() - qa.loudnessTarget()) <= qa.loudnessTolerance()
                && stats.truePeakDb() <= qa.truePeakMax();
        List<AudioQa.Track> tracks = List.of(new AudioQa.Track("narration", narration.file(), "Narration",
                LicenseRecord.generated(narration.provider())));
        context.usage().provider("ffmpeg-loudnorm");
        context.files().write(Stage.AUDIO_MIX, "audio_qa.json", new AudioQa(stats.integratedLufs(),
                stats.truePeakDb(), WavAudio.durationSec(mix), passed, tracks));
    }
}
