package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.format.VideoFormat;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunState;
import com.storyforge.orchestrator.run.RunStatus;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageExecution;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public record RunDto(
        Long id,
        Long seedTopicId,
        String topicTitle,
        VideoFormat format,
        String language,
        String profile,
        RunState state,
        RunStatus status,
        String statusReason,
        int targetSeconds,
        Long inspirationId,
        String angle,
        String voice,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        List<StageChipDto> stages) {

    public static RunDto from(Run run, List<StageExecution> executions) {
        List<StageChipDto> chips = Arrays.stream(Stage.values())
                .map(stage -> StageChipDto.from(stage, latest(executions, stage)))
                .toList();
        return new RunDto(run.getId(), run.getSeedTopicId(), run.getTopicTitle(), run.getFormat(), run.getLanguage(),
                run.getProfile(), run.getState(), run.getStatus(), run.getStatusReason(), run.getTargetSeconds(),
                run.getInspirationId(), run.getAngle(), run.getVoice(), run.getCreatedAt(), run.getUpdatedAt(),
                run.getStartedAt(), run.getFinishedAt(), chips);
    }

    private static Optional<StageExecution> latest(List<StageExecution> executions, Stage stage) {
        StageExecution latest = null;
        for (StageExecution execution : executions) {
            if (execution.getStage() == stage) {
                latest = execution;
            }
        }
        return Optional.ofNullable(latest);
    }
}
