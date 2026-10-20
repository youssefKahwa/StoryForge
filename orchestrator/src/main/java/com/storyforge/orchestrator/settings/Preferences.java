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
        String channelBrief,
        Boolean hostMascot,
        Boolean fairUseClips) {

    /** Short muted trailer clips for stories about a film/series/anime (owner chose this mode; on by default). */
    public boolean fairUseClipsEnabled() {
        return fairUseClips == null || fairUseClips;
    }

    /** The talking channel logo pops in during the video (on unless switched off). */
    public boolean hostEnabled() {
        return hostMascot == null || hostMascot;
    }

    public double musicGainOffsetDb() {
        return switch (musicVolume == null ? "normal" : musicVolume) {
            case "off" -> Double.NEGATIVE_INFINITY;
            case "low" -> -4;
            case "high" -> 3;
            default -> 0;
        };
    }
}
