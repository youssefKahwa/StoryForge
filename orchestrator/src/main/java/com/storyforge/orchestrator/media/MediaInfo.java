package com.storyforge.orchestrator.media;

public record MediaInfo(int width, int height, double fps, double durationSec, double videoDurationSec,
                        double audioDurationSec, boolean hasVideo, boolean hasAudio, String videoCodec,
                        String audioCodec) {
}
