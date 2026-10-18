package com.storyforge.orchestrator.sound;

import java.util.List;

public record SoundPlan(List<MusicSegment> music, List<SfxCue> sfx) {

    public record MusicSegment(String mood, String file, String track, double start, double end, double gainDb) {

        public double length() {
            return end - start;
        }
    }

    public record SfxCue(String preset, String file, double start, double gainDb, String reason) {
    }
}
