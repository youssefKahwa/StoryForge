package com.storyforge.orchestrator.model;

import java.util.List;
import java.util.Map;

public record Critique(
        Map<String, Double> scores,
        double average,
        double hook,
        boolean passed,
        int rewrites,
        List<String> coldRead,
        List<Flag> flags) {

    public record Flag(String sceneId, String severity, String issue, boolean resolved) {
    }

    public Critique evaluated(double minAverage, double minHook, int rewriteCount) {
        Map<String, Double> safe = scores == null ? Map.of() : scores;
        double avg = safe.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double hookScore = safe.getOrDefault("hook", 0.0);
        boolean pass = avg >= minAverage && hookScore >= minHook;
        return new Critique(safe, Math.round(avg * 100) / 100.0, hookScore, pass, rewriteCount,
                coldRead == null ? List.of() : coldRead, flags == null ? List.of() : flags);
    }
}
