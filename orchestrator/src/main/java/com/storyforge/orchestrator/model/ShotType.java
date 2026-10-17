package com.storyforge.orchestrator.model;

import java.util.Set;

public final class ShotType {

    public static final String ESTABLISHING = "establishing";
    public static final String CHARACTER = "character";
    public static final String DETAIL = "detail";
    public static final String MOTION_HERO = "motion_hero";
    public static final String MAP = "map";
    public static final String TIMELINE = "timeline";
    public static final String KINETIC_TEXT = "kinetic_text";
    public static final String CHART = "chart";
    public static final String ARCHIVAL_PD = "archival_pd";
    public static final String SYMBOLIC = "symbolic";

    private static final Set<String> GRAPHIC = Set.of(MAP, TIMELINE, KINETIC_TEXT, CHART);
    private static final Set<String> INTERRUPTS = Set.of(MAP, TIMELINE, KINETIC_TEXT, CHART);

    private ShotType() {
    }

    public static boolean isGraphic(String type) {
        return GRAPHIC.contains(type);
    }

    public static boolean isInterrupt(String type) {
        return INTERRUPTS.contains(type);
    }
}
