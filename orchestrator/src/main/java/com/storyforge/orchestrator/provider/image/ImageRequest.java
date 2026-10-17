package com.storyforge.orchestrator.provider.image;

public record ImageRequest(String prompt, String negativePrompt, long seed, int width, int height, String kind,
                           String label) {
}
