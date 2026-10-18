package com.storyforge.orchestrator.provider.image;

import java.nio.file.Path;

public interface ImageProvider {

    String name();

    ImageResult generate(ImageRequest request, Path outputImage);

    default boolean isCached(ImageRequest request) {
        return true;
    }

    default int remainingToday() {
        return Integer.MAX_VALUE;
    }
}
