package com.storyforge.orchestrator.model;

import java.util.List;

public record Outline(int totalSeconds, List<Beat> beats) {

    /** {@code points}: what the beat must say, in order (from the inspiration blueprint when there is one). */
    public record Beat(String beat, String chapterTitle, int targetSeconds, int wordBudget, String summary,
                       List<String> points, String rehook, List<String> claimIds) {

        public Beat withBudget(int seconds, int words) {
            return new Beat(beat, chapterTitle, seconds, words, summary, points == null ? List.of() : points, rehook,
                    claimIds == null ? List.of() : claimIds);
        }
    }
}
