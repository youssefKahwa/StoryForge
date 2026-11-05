package com.storyforge.orchestrator.provider.tts;

import com.storyforge.orchestrator.model.Scene;
import java.nio.file.Path;

public interface TtsProvider {

    String name();

    String voice();

    double synthesize(Scene scene, Path outputWav);

    default double synthesize(Scene scene, Path outputWav, double speedFactor, String voice) {
        return synthesize(scene, outputWav);
    }

    /**
     * The voice a whole run is narrated with, decided once before its first scene: the requested one, or a fallback
     * when that voice cannot be used right now (one video never mixes two voices).
     */
    default String resolveVoice(String requested) {
        return requested != null ? requested : voice();
    }
}
