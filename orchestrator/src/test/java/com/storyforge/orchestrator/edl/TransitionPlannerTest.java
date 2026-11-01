package com.storyforge.orchestrator.edl;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.clips.FilmClipFinderLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransitionPlannerTest {

    @Test
    void chaptersGetABurnOrZoomTenseScenesAGlitchAndTheRestHardCuts() {
        List<Shot> shots = shots(20, 5.0);
        List<Scene> scenes = List.of(scene("01", "calm"), scene("02", "tense"), scene("03", "calm"),
                scene("04", "calm"), scene("05", "calm"));
        List<EdlChapter> chapters = List.of(new EdlChapter(0, "One"), new EdlChapter(40, "Two"),
                new EdlChapter(80, "Three"));

        List<TransitionPlanner.Transition> plan = TransitionPlanner.plan(new ShotList(100, shots), scenes, chapters);

        assertThat(plan).hasSize(19);
        assertThat(kindAt(plan, 40.0)).isEqualTo("burn");
        assertThat(kindAt(plan, 80.0)).isEqualTo("zoom");
        assertThat(kindAt(plan, 20.0)).isEqualTo("glitch");
        assertThat(kindAt(plan, 5.0)).isEqualTo("cut");
        assertThat(plan).extracting(TransitionPlanner.Transition::kind).doesNotContain("fade");
        double last = -100;
        for (TransitionPlanner.Transition t : plan) {
            if (t.sfx() != null && !t.kind().equals("burn") && !(t.kind().equals("zoom") && t.at() == 80.0)) {
                assertThat(t.at() - last).isGreaterThanOrEqualTo(6);
            }
            if (t.sfx() != null) {
                last = t.at();
            }
        }
    }

    @Test
    void aPauseInsideASceneIsARevealWithAnImpactAndTheLongestOnesDipToBlack() {
        List<Shot> shots = shots(4, 5.0).stream().map(s -> new Shot(s.id(), "01", s.type(), s.start(), s.end(),
                s.prompt(), s.motion(), s.text(), s.seed())).toList();
        List<Word> words = List.of(word("one", 0, 4.6), word("two", 5.4, 9.8), word("three", 10.05, 14.5),
                word("four", 15.6, 19.5));
        Alignment alignment = new Alignment("test", 20, words, List.of());

        List<TransitionPlanner.Transition> plan = TransitionPlanner.plan(new ShotList(20, shots),
                List.of(scene("01", "tense")), List.of(new EdlChapter(0, "One")), alignment);

        assertThat(kindAt(plan, 5.0)).isEqualTo("impact");
        assertThat(kindAt(plan, 10.0)).isEqualTo("cut");
        assertThat(kindAt(plan, 15.0)).isEqualTo("dip");
    }

    @Test
    void longShotsGetAPunchInThatStaysQuietNextToATransition() {
        List<Shot> shots = new ArrayList<>(shots(3, 5.0));
        shots.add(new Shot("shot_004", "01", "detail", 15, 17, "p", "push_in", null, 1));
        ShotList list = new ShotList(17, shots);
        List<TransitionPlanner.Transition> transitions = List.of(
                new TransitionPlanner.Transition("shot_002", 7.4, "whip", "whip", -12, 0.2));

        List<TransitionPlanner.Punch> punches = TransitionPlanner.punches(list, Set.of("shot_003"), transitions);

        assertThat(punches).extracting(TransitionPlanner.Punch::shotId).containsExactly("shot_001", "shot_002");
        assertThat(punches.get(0).at()).isBetween(2.0, 3.0);
        assertThat(punches.get(0).sfx()).isEqualTo("shutter");
        assertThat(punches.get(1).sfx()).isNull();
        assertThat(punches).allSatisfy(p -> {
            assertThat(p.zoom()).isBetween(1.2, 1.4);
            assertThat(p.x()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void fairUseClipsStayUnderTheOwnersShare() {
        assertThat(FilmClipFinderLimits.maxClips(600, 0.20) * 4.0).isLessThanOrEqualTo(600 * 0.20);
        assertThat(FilmClipFinderLimits.maxClips(600, 0.70)).isEqualTo(105);
        assertThat(FilmClipFinderLimits.maxClips(10, 0.20)).isZero();
        assertThat(FilmClipFinderLimits.maxClips(600, 0)).isZero();
    }

    private static List<Shot> shots(int count, double length) {
        List<Shot> shots = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String scene = String.format("%02d", i / 4 + 1);
            String type = i == 6 ? "kinetic_text" : "detail";
            shots.add(new Shot("shot_%03d".formatted(i + 1), scene, type, i * length, i * length + length, "p",
                    "push_in", null, 1));
        }
        return shots;
    }

    private static String kindAt(List<TransitionPlanner.Transition> plan, double at) {
        return plan.stream().filter(t -> t.at() == at).map(TransitionPlanner.Transition::kind).findFirst().orElseThrow();
    }

    private static Word word(String text, double start, double end) {
        return new Word(text, start, end, "01");
    }

    private static Scene scene(String id, String tone) {
        return new Scene(id, 1, "beat", 20, null, tone, null, null, 1.0, 0, 0, List.of(), "text", null, List.of(),
                null, null, List.of(), List.of());
    }
}
