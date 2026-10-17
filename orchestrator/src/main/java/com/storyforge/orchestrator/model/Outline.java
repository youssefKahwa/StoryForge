package com.storyforge.orchestrator.model;

import java.util.List;

public record Outline(int totalSeconds, List<Beat> beats) {

    public record Beat(String beat, String chapterTitle, int targetSeconds, int wordBudget, String summary,
                       String rehook, List<String> claimIds) {

        public Beat withBudget(int seconds, int words) {
            return new Beat(beat, chapterTitle, seconds, words, summary, rehook,
                    claimIds == null ? List.of() : claimIds);
        }
    }
}
