package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageExecution;
import com.storyforge.orchestrator.stage.StageStatus;
import java.util.Optional;

public record StageChipDto(String stage, StageStatus status) {

    public static StageChipDto from(Stage stage, Optional<StageExecution> latest) {
        return new StageChipDto(stage.folder(), latest.map(StageExecution::getStatus).orElse(StageStatus.PENDING));
    }
}
