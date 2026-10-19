package com.storyforge.orchestrator.artifact;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Changes the owner asked for on a finished run. Image overrides are keyed by "sceneId#n" (the n-th shot of a scene,
 * from 1), which stays stable when another scene is rewritten.
 */
public record RunOverrides(Map<String, ShotOverride> shots, Map<String, String> sceneMusic, double musicGainDb,
                           List<String> packageNotes) {

    public static final String FILE = "overrides.json";

    public record ShotOverride(String prompt, int attempt, String note) {
    }

    public static RunOverrides empty() {
        return new RunOverrides(Map.of(), Map.of(), 0, List.of());
    }

    public RunOverrides {
        shots = shots == null ? Map.of() : shots;
        sceneMusic = sceneMusic == null ? Map.of() : sceneMusic;
        packageNotes = packageNotes == null ? List.of() : packageNotes;
    }

    public RunOverrides withShot(String key, ShotOverride override) {
        Map<String, ShotOverride> next = new LinkedHashMap<>(shots);
        next.put(key, override);
        return new RunOverrides(next, sceneMusic, musicGainDb, packageNotes);
    }

    public RunOverrides withSceneMusic(String sceneId, String mood) {
        Map<String, String> next = new LinkedHashMap<>(sceneMusic);
        next.put(sceneId, mood);
        return new RunOverrides(shots, next, musicGainDb, packageNotes);
    }

    public RunOverrides withMusicGain(double gainDb) {
        return new RunOverrides(shots, sceneMusic, gainDb, packageNotes);
    }

    public RunOverrides withPackageNote(String note) {
        List<String> next = new ArrayList<>(packageNotes);
        next.add(note);
        return new RunOverrides(shots, sceneMusic, musicGainDb, next);
    }
}
