package com.storyforge.orchestrator.stage;

import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.run.RunInfo;

public record StageContext(RunInfo run, RunFiles files, StageUsage usage) {
}
