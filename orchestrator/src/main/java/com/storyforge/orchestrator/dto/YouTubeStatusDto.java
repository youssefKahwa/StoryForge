package com.storyforge.orchestrator.dto;

public record YouTubeStatusDto(boolean configured, boolean connected, String channel, String publishMode,
                               String redirectUri, boolean telegram, boolean autopilot) {

    public static YouTubeStatusDto from(boolean configured, boolean connected, String channel, String publishMode,
                                        String redirectUri, boolean telegram, boolean autopilot) {
        return new YouTubeStatusDto(configured, connected, channel, publishMode, redirectUri, telegram, autopilot);
    }
}
