package com.storyforge.orchestrator.model;

import java.util.List;

public record ShotList(double durationSec, List<Shot> shots) {

    /** animation: a motion-kit explainer on a graphic shot instead of the plain text card (null otherwise). */
    public record Shot(String id, String sceneId, String type, double start, double end, String prompt,
                       String motion, String text, long seed, MotionGraphic animation) {

        public Shot(String id, String sceneId, String type, double start, double end, String prompt, String motion,
                    String text, long seed) {
            this(id, sceneId, type, start, end, prompt, motion, text, seed, null);
        }

        public Shot withTimes(double newStart, double newEnd) {
            return new Shot(id, sceneId, type, newStart, newEnd, prompt, motion, text, seed, animation);
        }

        public Shot withPrompt(String newPrompt, long newSeed) {
            return new Shot(id, sceneId, type, start, end, newPrompt, motion, text, newSeed, animation);
        }

        public Shot withAnimation(MotionGraphic newAnimation) {
            return new Shot(id, sceneId, type, start, end, prompt, motion, text, seed, newAnimation);
        }

        public double duration() {
            return end - start;
        }

        public boolean graphic() {
            return ShotType.isGraphic(type);
        }
    }
}
