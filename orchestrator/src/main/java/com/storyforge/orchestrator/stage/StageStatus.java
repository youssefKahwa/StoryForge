package com.storyforge.orchestrator.stage;

public enum StageStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    CACHED,
    FAILED,
    BLOCKED;

    public boolean isDone() {
        return this == SUCCEEDED || this == CACHED;
    }
}
