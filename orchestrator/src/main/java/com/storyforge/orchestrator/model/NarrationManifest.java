package com.storyforge.orchestrator.model;

import java.util.List;

public record NarrationManifest(String provider, String voice, double durationSec, String file,
                                List<SceneAudio> scenes, double speedFactor) {

    public record SceneAudio(String sceneId, String file, double offsetSec, double durationSec,
                             int silenceBeforeMs, int silenceAfterMs) {
    }
}
