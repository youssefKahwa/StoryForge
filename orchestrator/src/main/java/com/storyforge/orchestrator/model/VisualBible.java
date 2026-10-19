package com.storyforge.orchestrator.model;

import java.util.List;

/** Shared look of one video: era, light and the fixed appearance of recurring characters and places. */
public record VisualBible(String era, String look, List<Entry> characters, List<Entry> places) {

    public record Entry(String id, String name, String look) {
    }

    public static VisualBible empty() {
        return new VisualBible(null, null, List.of(), List.of());
    }
}
