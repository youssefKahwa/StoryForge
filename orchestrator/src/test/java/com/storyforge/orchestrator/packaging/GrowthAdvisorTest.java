package com.storyforge.orchestrator.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.GrowthReport;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GrowthAdvisorTest {

    private static final List<String> SEARCHES = List.of("fight club", "fight club ending explained",
            "fight club explained", "tyler durden", "fight club ending");

    private static GrowthReport report(String opening, String thumbnail, List<String> tags, Double introAt) {
        var research = new GrowthResearch.Research("Fight Club", SEARCHES, List.of(
                new MediaSvcClient.SearchHit("a", "Big channel essay", "Big", 3_000_000, 900, "u1", "c1"),
                new MediaSvcClient.SearchHit("b", "Small channel breakout", "Small", 400_000, 700, "u2", "c2")),
                Map.of("a", 2_000_000L, "b", 20_000L));
        String description = "Fight Club explained: the ending, and why Tyler is a warning.\nThe film's real twist.\n\n0:00 Hook";
        var chapters = List.of(new Edl.EdlChapter(0, "The Man With No Name"), new Edl.EdlChapter(60, "Hit Me"),
                new Edl.EdlChapter(200, "The Stranger on the Plane"));
        return GrowthAdvisor.build(new GrowthAdvisor.Input(research,
                List.of("Fight Club Ending Explained: The Warning Everyone Missed"), "Fight Club", opening, tags,
                description, thumbnail, chapters, 622, "Tell me in the comments, which painkiller is yours?",
                introAt, 3));
    }

    @Test
    void theOneFixIsTheMostImportantCheckThatFails() {
        GrowthReport good = report("Fight Club ends with a warning everyone missed. Tonight the ending is explained.",
                "Never Real", List.of("fight club"), 33.0);
        assertThat(good.checklist()).filteredOn(c -> !c.ok()).extracting(GrowthReport.Check::item)
                .containsExactly("Chapter titles are 3-5 word promises");
        assertThat(good.oneFix()).startsWith("Chapter titles are 3-5 word promises");

        GrowthReport baited = report("Top floor of an empty office tower, middle of the night. A gun.",
                "The Warning", List.of("fight club"), 0.0);
        // The intro playing first matters more than the opening, which matters more than the thumbnail.
        assertThat(baited.oneFix()).startsWith("The story starts before the channel intro");
        assertThat(baited.checklist()).filteredOn(c -> !c.ok()).extracting(GrowthReport.Check::item)
                .contains("The opening confirms the title", "The thumbnail text adds to the title in 3 words or less");
    }

    @Test
    void competitorsAreRankedByHowFarTheyBeatTheirOwnChannel() {
        GrowthReport r = report("x", "y", List.of(), 33.0);
        // 400K on a 20K channel (x20) teaches more than 3M on a 2M channel (x1.5).
        assertThat(r.competitors()).extracting(GrowthReport.Competitor::title)
                .containsExactly("Small channel breakout", "Big channel essay");
        assertThat(r.competitors().get(0).outlier()).isEqualTo(20.0);
    }

    @Test
    void threeTargetSearchesNameTheSubjectAndTagsStopAtFifteen() {
        assertThat(GrowthAdvisor.targets(SEARCHES, "Fight Club"))
                .containsExactly("fight club ending explained", "fight club explained", "fight club ending");
        List<String> many = java.util.stream.IntStream.range(0, 20).mapToObj(i -> "tag " + i).toList();
        GrowthReport r = report("x", "y", many, 33.0);
        assertThat(r.tags()).hasSize(15);
        assertThat(r.targets()).extracting(GrowthReport.Target::inTitle).containsExactly(true, false, true);
        assertThat(r.targets()).extracting(GrowthReport.Target::inDescription).containsExactly(false, true, false);
    }

    @Test
    void theWeekSpreadsTheShortsAroundTheVideo() {
        assertThat(GrowthAdvisor.plan(3)).extracting(GrowthReport.Step::day).containsExactly(1, 2, 3, 4, 6);
    }

    @Test
    void theTitleScorerRewardsATitleTheOpeningKeeps() {
        assertThat(TitleScorer.openingConfirms("Fight Club Ending Explained",
                "Fight Club ends with a warning. Tonight the ending is explained.")).isTrue();
        assertThat(TitleScorer.openingConfirms("Fight Club Ending Explained", "A gun, a tower, a countdown."))
                .isFalse();
        assertThat(TitleScorer.capsWords("The REAL Reason NOBODY Talks About IT")).isEqualTo(3);
    }
}
