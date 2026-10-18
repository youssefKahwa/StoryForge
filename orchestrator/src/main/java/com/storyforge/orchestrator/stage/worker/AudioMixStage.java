package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.edl.EdlBuilder;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.LoudnessStats;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.sound.MusicLibrary;
import com.storyforge.orchestrator.sound.SfxLibrary;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AudioMixStage implements StageWorker {

    public static final String MIX = "mix.wav";
    public static final String PLAN = "sound_plan.json";
    private static final int SAMPLE_RATE = 48_000;
    private static final double PEAK_MARGIN_DB = 0.5;
    private static final Logger log = LoggerFactory.getLogger(AudioMixStage.class);

    private final FfmpegService ffmpeg;
    private final MusicLibrary music;
    private final SfxLibrary sfx;
    private final SoundMixer mixer;
    private final StoryForgeProperties.Qa qa;
    private final boolean soundEnabled;

    public AudioMixStage(FfmpegService ffmpeg, MusicLibrary music, SfxLibrary sfx, SoundMixer mixer,
                         StoryForgeProperties properties) {
        this.ffmpeg = ffmpeg;
        this.music = music;
        this.sfx = sfx;
        this.mixer = mixer;
        this.qa = properties.qa();
        this.soundEnabled = properties.sound().enabled();
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
        if (soundEnabled) {
            List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
            Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
            Outline outline = files.read(Stage.OUTLINE, "outline.json", Outline.class);
            Map<String, List<MusicLibrary.Track>> library = music.scan();
            SoundPlan plan = SoundDesigner.plan(scenes, alignment, EdlBuilder.chapters(scenes, alignment, outline),
                    library, context.run().id(), sfx::file);
            if (plan.music().isEmpty()) {
                log.warn("Run {}: no music in {}, mixing voice and SFX only", context.run().id(), music.root());
            }
            files.write(Stage.AUDIO_MIX, PLAN, plan);
            loudnessInput = files.path(Stage.AUDIO_MIX, "premix.wav");
            mixer.mix(voice, plan, loudnessInput);
            tracks.addAll(soundTracks(plan, library));
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

    private static List<AudioQa.Track> soundTracks(SoundPlan plan, Map<String, List<MusicLibrary.Track>> library) {
        Map<String, AudioQa.Track> unique = new LinkedHashMap<>();
        Map<String, MusicLibrary.Track> byFile = new LinkedHashMap<>();
        library.values().forEach(list -> list.forEach(track -> byFile.put(track.file().toString(), track)));
        plan.music().forEach(segment -> {
            MusicLibrary.Track track = byFile.get(segment.file());
            unique.putIfAbsent(segment.file(), new AudioQa.Track("music", segment.track(), segment.track(),
                    track == null ? null : track.license()));
        });
        plan.sfx().forEach(cue -> unique.putIfAbsent(cue.file(), new AudioQa.Track("sfx", cue.preset() + ".wav",
                cue.preset(), SfxLibrary.LICENSE)));
        return new ArrayList<>(unique.values());
    }
}
