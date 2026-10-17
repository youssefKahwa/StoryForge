package com.storyforge.orchestrator.model;

import java.util.List;

public record ShotList(double durationSec, List<Shot> shots) {

    public record Shot(String id, String sceneId, String type, double start, double end, String prompt,
                       String motion, String text, long seed) {

        public double duration() {
            return end - start;
        }

        public boolean graphic() {
            return ShotType.isGraphic(type);
        }
    }
}
