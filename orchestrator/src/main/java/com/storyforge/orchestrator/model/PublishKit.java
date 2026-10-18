package com.storyforge.orchestrator.model;

import java.time.Instant;
import java.util.List;

public record PublishKit(
        String title,
        List<String> alternativeTitles,
        String description,
        List<String> tags,
        String tagsCsv,
        List<String> hashtags,
        String pinnedComment,
        String communityPost,
        List<String> thumbnails,
        String video,
        String captions,
        Instant publishAt,
        String publishAtLocal,
        String publishAtAudience,
        String category,
        String language,
        boolean madeForKids,
        boolean alteredContent,
        String alteredContentReason,
        List<String> studioChecklist,
        List<ShortClip> shorts,
        String exportDir) {

    public record ShortClip(String file, String title, String description, double seconds) {
    }
}
