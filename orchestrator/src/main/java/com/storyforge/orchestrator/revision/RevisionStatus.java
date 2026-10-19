package com.storyforge.orchestrator.revision;

public enum RevisionStatus {
    /** Written down, not applied yet: the owner can collect several notes and apply them together. */
    PENDING,
    /** Applied to the run's inputs; the run is re-rendering. */
    QUEUED,
    /** The re-rendered video contains it. */
    APPLIED,
    FAILED
}
