package com.storyforge.orchestrator.model;

import java.util.List;

public record PackageDraft(List<String> titles, String hookLine, List<String> tags, String pinnedComment,
                           String thumbnailText, List<ThumbnailConcept> thumbnails) {

    public record ThumbnailConcept(String text, String scene) {
    }
}
