package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.artifact.StoredFile;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.stage.StageExecution;
import java.math.BigDecimal;
import java.util.List;

public record RunDetailDto(
        RunDto run,
        List<StageExecutionDto> executions,
        List<RunFileDto> files,
        BigDecimal totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens) {

    public static RunDetailDto from(Run run, List<StageExecution> executions, List<StoredFile> files,
                                    BigDecimal totalCostUsd) {
        return new RunDetailDto(
                RunDto.from(run, executions),
                executions.stream().map(StageExecutionDto::from).toList(),
                files.stream().map(file -> RunFileDto.from(run.getId(), file)).toList(),
                totalCostUsd,
                executions.stream().mapToLong(StageExecution::getInputTokens).sum(),
                executions.stream().mapToLong(StageExecution::getOutputTokens).sum());
    }
}
