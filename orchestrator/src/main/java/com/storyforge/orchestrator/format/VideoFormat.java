package com.storyforge.orchestrator.format;

import java.util.Locale;

public enum VideoFormat {
    GREAT_STORIES,
    FILM_ANALYSIS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
