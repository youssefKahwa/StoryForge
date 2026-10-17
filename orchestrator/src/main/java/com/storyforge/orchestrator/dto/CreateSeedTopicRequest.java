package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.format.VideoFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSeedTopicRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 4000) String notes,
        VideoFormat format,
        @Size(max = 10) String language) {
}
