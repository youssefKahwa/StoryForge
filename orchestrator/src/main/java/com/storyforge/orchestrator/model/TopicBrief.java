package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.format.VideoFormat;
import java.util.List;
import java.util.Map;

public record TopicBrief(
        String title,
        VideoFormat format,
        String language,
        String angle,
        String corePromise,
        int targetSeconds,
        List<String> titleSeeds,
        List<Backup> backups,
        Map<String, Double> scores,
        String reasoning) {

    public record Backup(String title, String angle) {
    }

    public TopicBrief withRunFacts(String runTitle, VideoFormat runFormat, String runLanguage, int runTarget) {
        return new TopicBrief(runTitle, runFormat, runLanguage, angle, corePromise, runTarget,
                titleSeeds == null ? List.of() : titleSeeds, backups == null ? List.of() : backups,
                scores == null ? Map.of() : scores, reasoning);
    }
}
