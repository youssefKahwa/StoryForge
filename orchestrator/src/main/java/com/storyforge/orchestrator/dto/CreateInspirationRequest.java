package com.storyforge.orchestrator.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateInspirationRequest(
        @Size(max = 3) List<@Size(max = 300) String> urls,
        @Size(max = 200000) String pastedText) {
}
