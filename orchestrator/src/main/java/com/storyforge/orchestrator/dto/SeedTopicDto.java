package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.format.VideoFormat;
import com.storyforge.orchestrator.topic.SeedTopic;
import com.storyforge.orchestrator.topic.SeedTopicStatus;
import java.time.Instant;

public record SeedTopicDto(Long id, String title, String notes, VideoFormat format, String language,
                           SeedTopicStatus status, Instant createdAt) {

    public static SeedTopicDto from(SeedTopic topic) {
        return new SeedTopicDto(topic.getId(), topic.getTitle(), topic.getNotes(), topic.getFormat(),
                topic.getLanguage(), topic.getStatus(), topic.getCreatedAt());
    }
}
