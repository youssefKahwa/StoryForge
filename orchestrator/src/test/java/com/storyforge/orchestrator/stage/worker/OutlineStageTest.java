package com.storyforge.orchestrator.stage.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Outline;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutlineStageTest {

    private static Outline.Beat beat(String id, int seconds) {
        return new Outline.Beat(id, null, seconds, 0, "s", List.of(), null, List.of());
    }

    @Test
    void wordBudgetsFollowTheNarratorsOwnSpeed() {
        Outline draft = new Outline(600, List.of(beat("a", 100), beat("b", 200)));
        // A fast narrator (Magpie Jason, about 181 words a minute) needs more words for the same 10 minutes.
        Outline fast = OutlineStage.normalize(draft, 600, 181);
        Outline slow = OutlineStage.normalize(draft, 600, 135);
        int fastWords = fast.beats().stream().mapToInt(Outline.Beat::wordBudget).sum();
        int slowWords = slow.beats().stream().mapToInt(Outline.Beat::wordBudget).sum();
        assertThat(fast.beats()).extracting(Outline.Beat::targetSeconds).containsExactly(200, 400);
        assertThat(fastWords).isBetween(1750, 1760);
        assertThat(slowWords).isBetween(1305, 1315);
    }
}
