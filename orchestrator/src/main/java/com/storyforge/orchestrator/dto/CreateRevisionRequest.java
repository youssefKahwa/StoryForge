package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.revision.RevisionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRevisionRequest(
        @NotNull RevisionKind kind,
        @NotBlank @Size(max = 60) String target,
        Double atSec,
        @NotBlank @Size(max = 2000) String note) {
}
