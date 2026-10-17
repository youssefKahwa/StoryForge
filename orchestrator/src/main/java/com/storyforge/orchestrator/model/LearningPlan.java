package com.storyforge.orchestrator.model;

import java.time.Instant;
import java.util.List;

public record LearningPlan(String videoId, String status, List<Pull> pulls) {

    public record Pull(String label, Instant at, String status) {
    }
}
