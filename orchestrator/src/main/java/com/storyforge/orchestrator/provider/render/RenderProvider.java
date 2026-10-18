package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.visual.CardRenderer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface RenderProvider {

    int THUMB_WIDTH = 1280;
    int THUMB_HEIGHT = 720;

    String name();

    void render(Edl edl, Path runRoot, Path outputMp4);

    default boolean supportsShorts() {
        return false;
    }

    default void renderShort(Map<String, Object> spec, Path runRoot, Path outputMp4) {
        throw new UnsupportedOperationException(name() + " cannot render Shorts");
    }

    default void renderThumbnail(Path background, String text, List<String> palette, Path outputPng) {
        CardRenderer.thumbnail(outputPng, THUMB_WIDTH, THUMB_HEIGHT, text.hashCode(), text, background);
    }
}
