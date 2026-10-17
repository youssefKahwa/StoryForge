package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.stage.StageExecution;
import com.storyforge.orchestrator.stage.StageStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record StageExecutionDto(
        Long id,
        String stage,
        StageStatus status,
        String provider,
        String promptVersion,
        long inputTokens,
        long outputTokens,
        BigDecimal costUsd,
        String error,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs) {

    public static StageExecutionDto from(StageExecution execution) {
        return new StageExecutionDto(execution.getId(), execution.getStage().folder(), execution.getStatus(),
                execution.getProvider(), execution.getPromptVersion(), execution.getInputTokens(),
                execution.getOutputTokens(), execution.getCostUsd(), execution.getError(), execution.getStartedAt(),
                execution.getFinishedAt(), execution.getDurationMs());
    }
}
