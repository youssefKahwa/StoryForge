package com.storyforge.orchestrator.provider.image;

import java.nio.file.Path;

public interface ImageProvider {

    String name();

    ImageResult generate(ImageRequest request, Path outputPng);
}
