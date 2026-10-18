package com.storyforge.orchestrator.model;

public record VideoTranscript(String url, String videoId, String title, String channel, Integer durationSec,
                              String language, String source, String transcript) {
}
