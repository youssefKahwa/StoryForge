package com.storyforge.orchestrator.edl;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.clips.FilmClipFinderLimits;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitionPlannerTest {

    @Test
    void chaptersGetWhipOrZoomAndTheRestStaysCalm() {
        List<Shot> shots = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String scene = String.format("%02d", i / 4 + 1);
            String type = i == 6 ? "kinetic_text" : "detail";
            shots.add(new Shot("shot_%03d".formatted(i + 1), scene, type, i * 5.0, i * 5.0 + 5, "p", "push_in",
                    null, 1));
        }
        List<Scene> scenes = List.of(scene("01", "calm"), scene("02", "tense"), scene("03", "calm"),
                scene("04", "calm"), scene("05", "calm"));
        List<EdlChapter> chapters = List.of(new EdlChapter(0, "One"), new EdlChapter(40, "Two"),
                new EdlChapter(80, "Three"));

        List<TransitionPlanner.Transition> plan = TransitionPlanner.plan(new ShotList(100, shots), scenes, chapters);

        assertThat(plan).hasSize(19);
        assertThat(plan).filteredOn(t -> t.at() == 40.0).extracting(TransitionPlanner.Transition::kind)
                .containsExactly("whip");
        assertThat(plan).filteredOn(t -> t.at() == 80.0).extracting(TransitionPlanner.Transition::kind)
                .containsExactly("zoom");
        assertThat(plan).filteredOn(t -> t.at() == 20.0).extracting(TransitionPlanner.Transition::kind)
                .containsExactly("flash");
        assertThat(plan.stream().filter(t -> t.sfx() != null).count()).isLessThanOrEqualTo(7);
        double last = -100;
        for (TransitionPlanner.Transition t : plan) {
            if (t.sfx() != null && !t.kind().equals("whip") && !t.kind().equals("zoom")) {
                assertThat(t.at() - last).isGreaterThanOrEqualTo(8);
            }
            if (t.sfx() != null) {
                last = t.at();
            }
        }
    }

    @Test
    void fairUseClipsStayUnderFifteenPercent() {
        assertThat(FilmClipFinderLimits.maxClips(600)).isLessThanOrEqualTo(18);
        assertThat(FilmClipFinderLimits.maxClips(600) * 5.0).isLessThanOrEqualTo(600 * 0.15);
        assertThat(FilmClipFinderLimits.maxClips(30)).isZero();
    }

    private static Scene scene(String id, String tone) {
        return new Scene(id, 1, "beat", 20, null, tone, null, null, 1.0, 0, 0, List.of(), "text", null, List.of(),
                null, null, List.of(), List.of());
    }
}
