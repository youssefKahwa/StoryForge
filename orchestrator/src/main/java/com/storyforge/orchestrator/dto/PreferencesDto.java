package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.format.VideoFormat;
import com.storyforge.orchestrator.settings.Preferences;
import java.time.DayOfWeek;

public record PreferencesDto(
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

    public static PreferencesDto from(Preferences p) {
        return new PreferencesDto(p.defaultVoice(), p.defaultMinutes(), p.defaultFormat(), p.shortsEnabled(),
                p.shortsCount(), p.burnCaptions(), p.upscaleImages(), p.musicVolume(), p.publishDay(),
                p.publishTime(), p.publishZone(), p.channelBrief(), p.hostEnabled(),
                p.fairUseClipsEnabled(), (int) Math.round(p.clipShare() * 100));
    }

    public Preferences toPreferences() {
        return new Preferences(defaultVoice, defaultMinutes, defaultFormat, shortsEnabled, shortsCount, burnCaptions,
                upscaleImages, musicVolume, publishDay, publishTime, publishZone, channelBrief,
                hostMascot, fairUseClips, clipSharePercent);
    }
}
