package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.run.ProgressService;
import com.storyforge.orchestrator.run.RunStatus;
import com.storyforge.orchestrator.stage.Stage;
import java.time.Instant;
import java.util.List;

public record ProgressDto(long runId, RunStatus status, int percent, String stage, String stageLabel,
                          String stageDetail, Integer stagePercent, long elapsedSeconds, Long remainingSeconds,
                          Instant expectedAt, boolean onBattery, List<Step> steps) {

    public record Step(String stage, String label, String status, Long seconds, long estimateSeconds) {
    }

    public static ProgressDto from(ProgressService.RunProgress p) {
        return new ProgressDto(p.runId(), p.status(), p.percent(), p.stage() == null ? null : folder(p.stage()),
                p.stageLabel(), p.stageDetail(), p.stagePercent(), p.elapsedSeconds(), p.remainingSeconds(),
                p.expectedAt(), p.onBattery(), p.steps().stream().map(s -> new Step(folder(s.stage()), s.label(),
                        s.status(), s.seconds(), s.estimateSeconds())).toList());
    }

    private static String folder(Stage stage) {
        return stage.folder();
    }
}
