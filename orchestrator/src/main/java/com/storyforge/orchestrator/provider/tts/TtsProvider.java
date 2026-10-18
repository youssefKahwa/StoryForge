package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.model.Scene;
import java.nio.file.Path;

public interface TtsProvider {

    String name();

    String voice();

    double synthesize(Scene scene, Path outputWav);

    default double synthesize(Scene scene, Path outputWav, double speedFactor) {
        return synthesize(scene, outputWav);
    }
}
