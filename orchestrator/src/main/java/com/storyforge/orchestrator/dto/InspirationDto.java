package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.inspiration.Inspiration;
import com.storyforge.orchestrator.inspiration.InspirationStatus;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.VideoTranscript;
import java.time.Instant;
import java.util.List;

public record InspirationDto(Long id, InspirationStatus status, String error, List<String> urls,
                             List<InspirationVideoDto> videos, InspirationIdeas ideas, Instant createdAt) {

    public static InspirationDto from(Inspiration inspiration, List<VideoTranscript> transcripts,
                                      InspirationIdeas ideas) {
        return new InspirationDto(inspiration.getId(), inspiration.getStatus(), inspiration.getError(),
                inspiration.getUrlList(), transcripts.stream().map(InspirationVideoDto::from).toList(), ideas,
                inspiration.getCreatedAt());
    }
}
