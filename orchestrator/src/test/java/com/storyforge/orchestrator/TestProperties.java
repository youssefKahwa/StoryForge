package com.storyforge.orchestrator;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.util.List;

public final class TestProperties {

    private TestProperties() {
    }

    public static StoryForgeProperties withLlm(StoryForgeProperties.Llm llm) {
        return create(null, llm);
    }

    public static StoryForgeProperties withMedia(StoryForgeProperties.Media media) {
        return create(media, null);
    }

    private static StoryForgeProperties create(StoryForgeProperties.Media media, StoryForgeProperties.Llm llm) {
        return new StoryForgeProperties("target", "../prompts", "../config", List.of(), null, null, null, media, null,
                null, null, llm, null, null, null, null, null, null, null, "http://localhost:8000", null, null, null,
                null, false, null);
    }
}
