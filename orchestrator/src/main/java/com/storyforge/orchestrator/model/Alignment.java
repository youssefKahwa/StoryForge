package com.storyforge.orchestrator.model;

import java.util.List;

public record Alignment(String provider, double durationSec, List<Word> words, List<SceneSpan> scenes) {

    public record Word(String text, double start, double end, String sceneId) {

        public boolean endsPhrase() {
            String trimmed = text.replaceAll("[\"'”’)\\]]+$", "");
            return !trimmed.isEmpty() && ".,;:!?—–-".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0;
        }

        public boolean endsSentence() {
            String trimmed = text.replaceAll("[\"'”’)\\]]+$", "");
            return !trimmed.isEmpty() && ".!?".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0;
        }
    }

    public record SceneSpan(String sceneId, double start, double end) {
    }

    public List<Word> wordsOf(String sceneId) {
        return words.stream().filter(word -> word.sceneId().equals(sceneId)).toList();
    }
}
