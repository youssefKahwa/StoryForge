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

    public static StoryForgeProperties withDataDir(String dataDir) {
        return create(dataDir, null, null);
    }

    private static StoryForgeProperties create(StoryForgeProperties.Media media, StoryForgeProperties.Llm llm) {
        return create("target", media, llm);
    }

    private static StoryForgeProperties create(String dataDir, StoryForgeProperties.Media media,
                                               StoryForgeProperties.Llm llm) {
        return new StoryForgeProperties(dataDir, "../prompts", "../config", List.of(), null, null, null, media, null,
                null, null, llm, null, null, null, null, null, null, null, "http://localhost:8000", null, null, null,
                null, false, null);
    }
}
