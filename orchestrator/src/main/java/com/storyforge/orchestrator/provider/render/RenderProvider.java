package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.visual.CardRenderer;
import java.nio.file.Path;
import java.util.List;

public interface RenderProvider {

    int THUMB_WIDTH = 1280;
    int THUMB_HEIGHT = 720;

    String name();

    void render(Edl edl, Path runRoot, Path outputMp4);

    default void renderThumbnail(Path background, String text, List<String> palette, Path outputPng) {
        CardRenderer.thumbnail(outputPng, THUMB_WIDTH, THUMB_HEIGHT, text.hashCode(), text, background);
    }
}
