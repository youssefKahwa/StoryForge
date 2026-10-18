package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.voice.VoiceCatalog;

public record VoiceDto(String id, String label, String gender, String accent, String grade, boolean isDefault) {

    public static VoiceDto from(VoiceCatalog.Voice voice, String defaultVoice) {
        return new VoiceDto(voice.id(), voice.label(), voice.gender(), voice.accent(), voice.grade(),
                voice.id().equals(defaultVoice));
    }
}
