package com.storyforge.orchestrator.model;

import java.util.ArrayList;
import java.util.List;

public record Scene(
        String id,
        int index,
        String beat,
        double durationTargetSec,
        String voice,
        String tone,
        String pace,
        String emotion,
        double speed,
        int silenceBeforeMs,
        int silenceAfterMs,
        List<Segment> segments,
        String narration,
        String visual,
        List<SfxCue> sfx,
        MusicCue music,
        String onscreen,
        List<String> claims,
        List<String> warnings) {

    public record Segment(String kind, String text, boolean emphasis, int pauseMs) {

        public static Segment text(String text, boolean emphasis) {
            return new Segment("text", text, emphasis, 0);
        }

        public static Segment pause(int pauseMs) {
            return new Segment("pause", null, false, pauseMs);
        }

        public boolean silent() {
            return "pause".equals(kind);
        }
    }

    public record SfxCue(String name, String anchor) {
    }

    public record MusicCue(String mood, double intensity) {
    }

    public List<String> words() {
        List<String> words = new ArrayList<>();
        if (narration == null || narration.isBlank()) {
            return words;
        }
        for (String token : narration.strip().split("\\s+")) {
            boolean wordless = token.codePoints().noneMatch(Character::isLetterOrDigit);
            if (wordless && !words.isEmpty()) {
                words.set(words.size() - 1, words.get(words.size() - 1) + token);
            } else {
                words.add(token);
            }
        }
        return words;
    }
}
