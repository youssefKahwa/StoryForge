package com.storyforge.orchestrator.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record Critique(
        Map<String, Double> scores,
        double average,
        double hook,
        boolean passed,
        int rewrites,
        int words,
        double estimatedSeconds,
        int targetSeconds,
        boolean lengthOk,
        List<String> coldRead,
        List<Flag> flags) {

    public record Flag(String sceneId, String severity, String issue, boolean resolved) {
    }

    /** Below this, a script is not one story told in scenes yet: it goes back to the rewrite like a failed hook. */
    public static final double MIN_STORY = 7;
    private static final List<String> STORY = List.of("story_drive", "scenes_not_summary");

    public Critique evaluated(double minAverage, double minHook, int rewriteCount) {
        Map<String, Double> safe = scores == null ? Map.of() : scores;
        double avg = safe.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double hookScore = safe.getOrDefault("hook", 0.0);
        boolean story = STORY.stream().allMatch(key -> !safe.containsKey(key) || safe.get(key) >= MIN_STORY);
        boolean pass = avg >= minAverage && hookScore >= minHook && story;
        return new Critique(safe, Math.round(avg * 100) / 100.0, hookScore, pass, rewriteCount, words, estimatedSeconds,
                targetSeconds, lengthOk, coldRead == null ? List.of() : coldRead, flags == null ? List.of() : flags);
    }

    public Critique withLength(int wordCount, double seconds, int target, double tolerance, int wordsPerMinute) {
        boolean ok = target <= 0 || Math.abs(seconds - target) <= target * tolerance;
        List<Flag> allFlags = new ArrayList<>(flags);
        if (!ok) {
            long wordDelta = Math.round(Math.abs(seconds - target) * wordsPerMinute / 60.0);
            allFlags.add(new Flag("all", "major", "Script runs about " + Math.round(seconds) + "s when narrated; the "
                    + "target is " + target + "s. " + (seconds > target ? "Cut" : "Add") + " about " + wordDelta
                    + " words (currently " + wordCount + ").", false));
        }
        double rounded = Math.round(seconds * 10) / 10.0;
        return new Critique(scores, average, hook, passed && ok, rewrites, wordCount, rounded, target, ok, coldRead,
                List.copyOf(allFlags));
    }

    /**
     * Adds the spoken-language findings (SpokenCheck). A major one (robot rhythm, essay rhythm, written-only
     * punctuation or asides) fails the round, so the rewrite pass fixes it like an editor's flag.
     */
    public Critique withSpoken(List<Flag> spokenFlags, boolean major) {
        if (spokenFlags == null || spokenFlags.isEmpty()) {
            return this;
        }
        List<Flag> allFlags = new ArrayList<>(flags);
        allFlags.addAll(spokenFlags);
        return new Critique(scores, average, hook, passed && !major, rewrites, words, estimatedSeconds, targetSeconds,
                lengthOk, coldRead, List.copyOf(allFlags));
    }
}
