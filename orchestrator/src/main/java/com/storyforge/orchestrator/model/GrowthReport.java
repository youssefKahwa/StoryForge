package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import java.util.List;

/**
 * The run's "Growth" panel: titles ranked with the reasons, what people search on YouTube about it (and whether our
 * package uses it), the videos that already get views on it, the tags, the top of the description, and a checklist.
 */
public record GrowthReport(String name, List<ScoredTitle> titles, List<Search> searches,
                           List<Competitor> competitors, List<String> tags, String descriptionTop,
                           List<Check> checklist) {

    public record Search(String phrase, boolean used) {
    }

    public record Competitor(String title, String channel, long views, double durationSec, String url) {
    }

    public record Check(String item, boolean ok, String detail) {
    }
}
