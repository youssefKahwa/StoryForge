package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.model.VideoTranscript;

public record InspirationVideoDto(String url, String title, String channel, Integer durationSec, String language,
                                  String source, int transcriptChars) {

    public static InspirationVideoDto from(VideoTranscript transcript) {
        return new InspirationVideoDto(transcript.url(), transcript.title(), transcript.channel(),
                transcript.durationSec(), transcript.language(), transcript.source(),
                transcript.transcript() == null ? 0 : transcript.transcript().length());
    }
}
