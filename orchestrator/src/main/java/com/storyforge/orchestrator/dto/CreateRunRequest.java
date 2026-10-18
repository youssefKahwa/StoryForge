package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.format.VideoFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record CreateRunRequest(
        Long seedTopicId,
        @Size(max = 300) String topicTitle,
        VideoFormat format,
        @Size(max = 10) String language,
        @DecimalMin("0.5") @DecimalMax("60") Double targetMinutes,
        Long inspirationId,
        @Size(max = 2000) String angle,
        @Size(max = 60) String voice) {
}
