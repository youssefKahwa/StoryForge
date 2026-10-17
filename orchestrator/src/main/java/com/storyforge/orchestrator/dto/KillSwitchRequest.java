package com.storyforge.orchestrator.dto;

import jakarta.validation.constraints.NotNull;

public record KillSwitchRequest(@NotNull Boolean paused) {
}
