package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "tts", havingValue = "kokoro")
public class KokoroTtsProvider implements TtsProvider {

    private final MediaSvcClient media;
    private final StoryForgeProperties.Narration narration;

    public KokoroTtsProvider(MediaSvcClient media, StoryForgeProperties properties) {
        this.media = media;
        this.narration = properties.narration();
    }

    @Override
    public String name() {
        return "kokoro";
    }

    @Override
    public String voice() {
        return narration.voice();
    }

    @Override
    public double synthesize(Scene scene, Path outputWav) {
        return synthesize(scene, outputWav, 1.0);
    }

    @Override
    public double synthesize(Scene scene, Path outputWav, double speedFactor) {
        double sceneSpeed = scene.speed() > 0 ? scene.speed() : 1.0;
        return media.tts(scene.segments(), narration.voice(), narration.speed() * sceneSpeed * speedFactor,
                narration.lang(), outputWav);
    }
}
