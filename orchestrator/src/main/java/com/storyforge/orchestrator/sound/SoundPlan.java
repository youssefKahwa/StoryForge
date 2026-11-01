package com.storyforge.orchestrator.sound;

import java.util.List;

public record SoundPlan(List<MusicSegment> music, List<SfxCue> sfx) {

    public record MusicSegment(String mood, String file, String track, double start, double end, double gainDb) {

        public double length() {
            return end - start;
        }
    }

    /**
     * duration: seconds to play (looped if the file is shorter, faded in and out), null to play the whole file once.
     * Ambience beds and risers that must stop on a cut use it.
     */
    public record SfxCue(String preset, String file, double start, double gainDb, String reason, Double duration) {

        public SfxCue(String preset, String file, double start, double gainDb, String reason) {
            this(preset, file, start, gainDb, reason, null);
        }
    }
}
