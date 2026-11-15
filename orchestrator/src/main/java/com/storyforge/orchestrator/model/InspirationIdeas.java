package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.format.VideoFormat;
import java.util.List;

/**
 * What the owner's inspiration videos say and how they say it. {@code blueprint} and {@code voice} are absent in
 * inspirations analysed before prompt inspire v0.2; the outline then falls back to the format's beats. With a main
 * video (inspire v0.4) the blueprint, voice and story are the main video's alone; {@code extras} holds the useful
 * details of the other videos (facts, examples, takes), which never become parts.
 */
public record InspirationIdeas(
        String topic,
        String story,
        List<String> leads,
        List<String> whatWorked,
        List<Part> blueprint,
        Voice voice,
        List<Angle> angles,
        VideoFormat suggestedFormat,
        Integer suggestedMinutes,
        List<String> cautions,
        List<String> extras) {

    public static final int MIN_MINUTES = 8;
    public static final int MAX_MINUTES = 12;

    public record Angle(String title, String angle, String whyOriginal) {
    }

    /** One part of the inspiration videos' running order: what they cover there, in order. */
    public record Part(String title, List<String> points, Double share) {
    }

    public record Voice(String persona, String energy, String humor, String viewer, List<String> devices) {
    }

    public boolean hasBlueprint() {
        return blueprint != null && !blueprint.isEmpty();
    }

    /** Suggested length kept inside the monetizable range (8+ minutes for mid-roll ads). */
    public Integer suggestedMinutes() {
        return suggestedMinutes == null ? null
                : Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, suggestedMinutes));
    }
}
