package com.storyforge.orchestrator.model;

import java.util.List;

/**
 * segments: what was said when (seconds); heatmap: YouTube's "most replayed" curve (value 0-1 per stretch). Both are
 * null for transcripts read before they were kept.
 */
public record VideoTranscript(String url, String videoId, String title, String channel, Integer durationSec,
                              String language, String source, String transcript, List<Line> segments,
                              List<Heat> heatmap) {

    public VideoTranscript(String url, String videoId, String title, String channel, Integer durationSec,
                           String language, String source, String transcript) {
        this(url, videoId, title, channel, durationSec, language, source, transcript, null, null);
    }

    public record Line(double start, double end, String text) {
    }

    public record Heat(double start, double end, double value) {
    }

    public boolean timed() {
        return segments != null && !segments.isEmpty();
    }
}
