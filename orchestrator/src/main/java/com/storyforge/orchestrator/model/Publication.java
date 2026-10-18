package com.storyforge.orchestrator.model;

import java.time.Instant;
import java.util.List;

public record Publication(String provider, String videoId, String url, String privacy, Instant uploadedAt,
                          Instant publishAt, boolean disclosure, List<String> warnings) {

    public Publication vetoed() {
        return new Publication(provider, videoId, url, "private-vetoed", uploadedAt, null, disclosure, warnings);
    }
}
