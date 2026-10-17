package com.storyforge.orchestrator.run;

public enum RunStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED;

    public boolean isResumable() {
        return this == FAILED || this == BLOCKED || this == CANCELLED;
    }
}
