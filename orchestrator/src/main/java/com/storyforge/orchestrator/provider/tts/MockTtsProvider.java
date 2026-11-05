package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.WavAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.Scene.Segment;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "tts", havingValue = "mock", matchIfMissing = true)
public class MockTtsProvider implements TtsProvider {

    private final int wordsPerMinute;

    public MockTtsProvider(StoryForgeProperties properties) {
        this.wordsPerMinute = properties.video().wordsPerMinute();
    }

    @Override
    public String name() {
        return "mock-tts";
    }

    @Override
    public String voice() {
        return "sine";
    }

    @Override
    public double synthesize(Scene scene, Path outputWav) {
        double wordSeconds = 60.0 / (wordsPerMinute * (scene.speed() > 0 ? scene.speed() : 1.0));
        WavAudio.Builder audio = WavAudio.builder();
        for (Segment segment : scene.segments()) {
            if (segment.silent()) {
                audio.silence((segment.pauseMs() > 0 ? segment.pauseMs() : 600) / 1000.0);
                continue;
            }
            if (!segment.spoken()) {
                continue;
            }
            for (String word : segment.text().split("\\s+")) {
                if (word.codePoints().noneMatch(Character::isLetterOrDigit)) {
                    continue;
                }
                double frequency = 170 + Math.floorMod(word.hashCode(), 60);
                audio.tone(wordSeconds * 0.8, frequency, segment.emphasis() ? 0.45 : 0.3)
                        .silence(wordSeconds * 0.2);
            }
        }
        audio.write(outputWav);
        return audio.durationSec();
    }
}
