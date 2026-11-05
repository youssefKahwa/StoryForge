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

        /** [mood: hype|neutral|serious|angry]: how the following sentences are said, until the next mood. */
        public static Segment mood(String mood) {
            return new Segment("mood", mood, false, 0);
        }

        /** [beat]: a held pause before a reveal; the delivery engine sizes it (0 = its default). */
        public static Segment beat(int pauseMs) {
            return new Segment("beat", null, false, pauseMs);
        }

        /** @say: word = how it is said (a respelling, or ipa:...), for words a voice gets wrong. */
        public static Segment say(String rule) {
            return new Segment("say", rule, false, 0);
        }

        public boolean silent() {
            return "pause".equals(kind) || "beat".equals(kind);
        }

        /** Words that are said (not a delivery mark or a pronunciation rule). */
        public boolean spoken() {
            return kind == null || "text".equals(kind);
        }
    }

    public record SfxCue(String name, String anchor) {
    }

    public record MusicCue(String mood, double intensity) {
    }

    public Scene withMusic(MusicCue cue) {
        return new Scene(id, index, beat, durationTargetSec, voice, tone, pace, emotion, speed, silenceBeforeMs,
                silenceAfterMs, segments, narration, visual, sfx, cue, onscreen, claims, warnings);
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
