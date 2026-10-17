package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.format.VideoFormat;

public record TopicCandidate(String title, String source, VideoFormat format, String language, String notes,
                             double signalScore, boolean ownerPick) {
}
