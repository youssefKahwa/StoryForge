package com.storyforge.orchestrator.model;

import java.util.List;

/** segments: what was said when (seconds); null for transcripts read before timing was kept. */
public record VideoTranscript(String url, String videoId, String title, String channel, Integer durationSec,
                              String language, String source, String transcript, List<Line> segments) {

    public VideoTranscript(String url, String videoId, String title, String channel, Integer durationSec,
                           String language, String source, String transcript) {
        this(url, videoId, title, channel, durationSec, language, source, transcript, null);
    }

    public record Line(double start, double end, String text) {
    }

    public boolean timed() {
        return segments != null && !segments.isEmpty();
    }
}
