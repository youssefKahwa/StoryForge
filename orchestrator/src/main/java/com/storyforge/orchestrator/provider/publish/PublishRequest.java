package com.storyforge.orchestrator.provider.publish;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record PublishRequest(long runId, String title, String description, List<String> tags, Path video,
                             Path thumbnail, Path captions, Instant publishAt, boolean disclosure) {
}
