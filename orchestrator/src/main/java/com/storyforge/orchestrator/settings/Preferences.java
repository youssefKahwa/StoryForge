package com.storyforge.orchestrator.settings;

import com.storyforge.orchestrator.format.VideoFormat;
import java.time.DayOfWeek;

public record Preferences(
        String defaultVoice,
        double defaultMinutes,
        VideoFormat defaultFormat,
        boolean shortsEnabled,
        int shortsCount,
        boolean burnCaptions,
        boolean upscaleImages,
        String musicVolume,
        DayOfWeek publishDay,
        String publishTime,
        String publishZone,
        String channelBrief) {

    public double musicGainOffsetDb() {
        return switch (musicVolume == null ? "normal" : musicVolume) {
            case "off" -> Double.NEGATIVE_INFINITY;
            case "low" -> -4;
            case "high" -> 3;
            default -> 0;
        };
    }
}
