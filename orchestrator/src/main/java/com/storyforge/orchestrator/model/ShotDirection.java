package com.storyforge.orchestrator.model;

import java.util.List;

/** Raw answer of the shot director prompt, before validation. */
public record ShotDirection(List<ShotPrompt> shots, List<OverlayDraft> overlays) {

    public record ShotPrompt(String id, String prompt, RealMedia real) {
    }

    public record OverlayDraft(String kind, String shot, String title, String subtitle) {
    }
}
