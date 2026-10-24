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

    /** New images allowed per day by the free tier (unlimited for providers without a quota). */
    default int dailyLimit() {
        return Integer.MAX_VALUE;
    }
}
