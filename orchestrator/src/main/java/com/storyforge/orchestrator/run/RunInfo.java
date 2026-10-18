package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.format.VideoFormat;

public record RunInfo(long id, String topicTitle, VideoFormat format, String language, int targetSeconds,
                      int cacheSalt, Long inspirationId, String angle, String voice) {

    public static RunInfo from(Run run) {
        return new RunInfo(run.getId(), run.getTopicTitle(), run.getFormat(), run.getLanguage(),
                run.getTargetSeconds(), run.getCacheSalt(), run.getInspirationId(), run.getAngle(), run.getVoice());
    }
}
