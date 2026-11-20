package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import java.util.List;

/**
 * The run's "Growth" panel: titles ranked with the reasons, what people search on YouTube about it (and whether our
 * package uses it), the 3 searches the video targets, the videos that already get views on it (with how far each beat
 * its own channel's usual views), the tags, the top of the description, a checklist, the one fix to make first and a
 * posting plan.
 */
public record GrowthReport(String name, List<ScoredTitle> titles, List<Search> searches,
                           List<Competitor> competitors, List<String> tags, String descriptionTop,
                           List<Check> checklist, List<Target> targets, String oneFix, List<Step> postingPlan) {

    public record Search(String phrase, boolean used) {
    }

    /** A search the video should be found for: is it in the title, in the first two description lines? */
    public record Target(String phrase, boolean inTitle, boolean inDescription) {
    }

    /** outlier: views divided by the channel's median (0 when unknown); above 2 the packaging beat the channel. */
    public record Competitor(String title, String channel, long views, double durationSec, String url,
                             long medianViews, double outlier) {
    }

    public record Check(String item, boolean ok, String detail) {
    }

    public record Step(int day, String what) {
    }
}
