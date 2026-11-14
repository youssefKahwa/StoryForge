package com.storyforge.orchestrator.model;

import java.util.List;
import java.util.Map;

/**
 * What ended up on screen, per kind of picture, with every repeat and every AI picture and why it was needed.
 * beats: beats in the music; cuts: every picture cut; cutsOnBeat: share of those within 60 ms of a beat;
 * creatorShots: the footage shots showing a moment the owner's main video showed.
 */
public record FootageReport(double durationSec, Map<String, Double> secondsByType, int footageShots, int momentsUsed,
                            int maxUses, List<Repeat> repeats, List<Picture> aiPictures, int beats, int cuts,
                            double cutsOnBeat, List<String> creatorShots) {

    public static final String AI_PICTURE = "ai_picture";
    public static final String REUSED = "reused";

    /** what: "moment 12" or "picture shot_004"; shots: every shot that shows it. */
    public record Repeat(String what, List<String> shots) {
    }

    public record Picture(String shot, double seconds, String provider, String reason) {
    }

    /** Share of the video's time filled by AI pictures, reused ones included. */
    public double aiShare() {
        double seconds = secondsByType.getOrDefault(AI_PICTURE, 0.0) + secondsByType.getOrDefault(REUSED, 0.0);
        return seconds / Math.max(1, durationSec);
    }

    public List<Repeat> overLimit() {
        return repeats.stream().filter(repeat -> repeat.shots().size() > maxUses).toList();
    }
}
