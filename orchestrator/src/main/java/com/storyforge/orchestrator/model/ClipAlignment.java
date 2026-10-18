package com.storyforge.orchestrator.model;

import java.util.List;

public record ClipAlignment(List<ClipWord> words, String transcript, double wer, double durationSec) {

    public record ClipWord(String text, double start, double end) {
    }
}
