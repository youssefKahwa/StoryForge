package com.storyforge.orchestrator.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.TestProperties;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.media.MediaInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SoundMixerTest {

    private final FfmpegService ffmpeg = new FfmpegService(TestProperties.withMedia(
            new StoryForgeProperties.Media("ffmpeg", "ffprobe", 120)), JsonMapper.builder().build());

    @Test
    void mixesVoiceMusicAndSfxIntoAFileAsLongAsTheVoice() throws Exception {
        Path dir = Files.createDirectories(Path.of("target", "sound-mixer-test"));
        Path voice = tone(dir.resolve("voice.wav"), 220, 12, "mono");
        Path music = tone(dir.resolve("music.wav"), 110, 5, "stereo");
        Path hit = tone(dir.resolve("hit.wav"), 60, 1, "stereo");
        SoundPlan plan = new SoundPlan(
                List.of(new SoundPlan.MusicSegment("dread", music.toString(), "dread/music.wav", 0, 7, -18),
                        new SoundPlan.MusicSegment("hope", music.toString(), "hope/music.wav", 6.25, 12, -16)),
                List.of(new SoundPlan.SfxCue("low_hit", hit.toString(), 3.0, -10, "test")));
        Path out = dir.resolve("mix.wav");

        new SoundMixer(ffmpeg).mix(voice, plan, out);

        MediaInfo info = ffmpeg.probe(out);
        assertThat(info.hasAudio()).isTrue();
        assertThat(info.durationSec()).isBetween(11.9, 12.1);
    }

    private Path tone(Path file, int frequency, int seconds, String layout) {
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "sine=frequency=%d:duration=%d".formatted(frequency, seconds), "-ac", layout.equals("mono") ? "1" : "2",
                "-ar", "24000", file.toString()));
        return file;
    }
}
