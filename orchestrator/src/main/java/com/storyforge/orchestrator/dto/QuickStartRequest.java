package com.storyforge.orchestrator.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

/** mainUrl: the owner's main video, whose script the new one follows part by part (optional). */
public record QuickStartRequest(
        @Size(max = 3) List<@Size(max = 300) String> urls,
        @Size(max = 200000) String pastedText,
        @Size(max = 300) String topic,
        @Size(max = 2000) String notes,
        @Size(max = 300) String mainUrl) {
}
