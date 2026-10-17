package com.storyforge.orchestrator.model;

import java.time.Instant;

public record ScheduleSlot(Instant uploadAt, Instant vetoDeadline, Instant publishAt, String zone) {
}
