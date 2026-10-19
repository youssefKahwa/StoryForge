package com.storyforge.orchestrator.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Overlay;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotDirection.OverlayDraft;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotDirectorTest {

    private static final String NARRATION = "In February 1959, nine hikers set out into the Ural Mountains. "
            + "Their last diary entry reads: we now know that snowmen exist. By 1960 the case was closed.";

    @Test
    void keepsOnlyOverlaysThatTheNarrationActuallySays() {
        ShotList shots = new ShotList(120, List.of(
                shot("shot_001", 0, 6), shot("shot_002", 25, 33), shot("shot_003", 50, 56),
                shot("shot_004", 80, 86), shot("shot_005", 90, 96)));
        List<OverlayDraft> drafts = List.of(
                new OverlayDraft("location", "shot_001", "Ural Mountains, USSR", "February 1959"),
                new OverlayDraft("quote", "shot_002", "“we now know that snowmen exist”", "last diary entry"),
                new OverlayDraft("quote", "shot_003", "the yeti is real and hungry tonight", "Igor"),
                new OverlayDraft("year", "shot_004", "1960", "ignored"),
                new OverlayDraft("location", "shot_005", "Moscow", ""));

        List<Overlay> overlays = ShotDirector.overlays(drafts, shots, List.of(scene(NARRATION)));

        assertThat(overlays).extracting(Overlay::kind).containsExactly("location", "quote", "year");
        assertThat(overlays.get(0).subtitle()).isEqualTo("February 1959");
        assertThat(overlays.get(1).title()).isEqualTo("we now know that snowmen exist");
        assertThat(overlays.get(1).subtitle()).isEqualTo("last diary entry");
        assertThat(overlays.get(2).subtitle()).isEmpty();
    }

    @Test
    void dropsInventedDatesAndOverlaysThatComeTooClose() {
        ShotList shots = new ShotList(120, List.of(shot("shot_001", 0, 6), shot("shot_002", 8, 14)));
        List<Overlay> overlays = ShotDirector.overlays(List.of(
                new OverlayDraft("location", "shot_001", "Ural Mountains", "March 1961"),
                new OverlayDraft("year", "shot_002", "1959", "")), shots, List.of(scene(NARRATION)));

        assertThat(overlays).hasSize(1);
        assertThat(overlays.get(0).subtitle()).isEmpty();
    }

    private static Shot shot(String id, double start, double end) {
        return new Shot(id, "01", "establishing", start, end, "prompt", "push_in", null, 1);
    }

    private static Scene scene(String narration) {
        return new Scene("01", 1, "context", 30, null, null, null, null, 1.0, 0, 0, List.of(), narration, null,
                List.of(), null, null, List.of(), List.of());
    }
}
