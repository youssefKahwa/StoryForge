package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.run.QuickStartService;

public record QuickStartDto(Long runId, Long inspirationId, String message) {

    public static QuickStartDto from(QuickStartService.Result result) {
        return new QuickStartDto(result.runId(), result.inspirationId(), result.message());
    }
}
