package com.storyforge.orchestrator.format;

import java.util.List;

public record FormatDefinition(
        String id,
        String name,
        int minMinutes,
        int maxMinutes,
        List<Beat> beats,
        Style style) {

    public record Beat(String id, String purpose, double share) {
    }

    public record Style(String artDirection, String promptPrefix, String negativePrompt, List<String> palette,
                        List<String> fonts, boolean realistic) {
    }

    public boolean hasBeat(String beatId) {
        return beats.stream().anyMatch(beat -> beat.id().equals(beatId));
    }
}
