package com.storyforge.orchestrator.model;

import java.time.Instant;

public record Publication(String provider, String videoId, String url, String privacy, Instant uploadedAt,
                          Instant publishAt, boolean disclosure) {
}
