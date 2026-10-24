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
        Boolean fairUseClips,
        Integer clipSharePercent) {

    public static final int DEFAULT_CLIP_SHARE = 20;
    public static final int MAX_CLIP_SHARE = 25;
    public static final double MIN_LONG_MINUTES = 8;

    /** Short muted trailer clips for stories about a film/series/anime (owner chose this mode; on by default). */
    public boolean fairUseClipsEnabled() {
        return fairUseClips == null || fairUseClips;
    }

    /** Most of the video that may be fair-use trailer clips, as a fraction (0.20 by default). */
    public double clipShare() {
        int percent = clipSharePercent == null ? DEFAULT_CLIP_SHARE : clipSharePercent;
        return Math.max(0, Math.min(MAX_CLIP_SHARE, percent)) / 100.0;
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
