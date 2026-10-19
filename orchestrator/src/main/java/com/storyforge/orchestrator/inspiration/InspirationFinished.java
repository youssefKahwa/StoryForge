package com.storyforge.orchestrator.inspiration;

/** Published when an inspiration analysis ends, so Quick start can launch the run. */
public record InspirationFinished(long id, InspirationStatus status, String error) {
}
