package com.storyforge.orchestrator.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.MotionGraphic;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MotionDirectorTest {

    private static Shot shot(String id, String scene, String type, double start, double end) {
        return new Shot(id, scene, type, start, end, "p", "none", "kinetic_text".equals(type) ? "card" : null, 1);
    }

    private static List<Alignment.Word> words(double from, String... text) {
        List<Alignment.Word> out = new ArrayList<>();
        double t = from;
        for (String w : text) {
            out.add(new Alignment.Word(w, t, t + 0.3, "02"));
            t += 0.4;
        }
        return out;
    }

    private static MotionDirector.Draft draft(String shot, String template, String... labels) {
        List<MotionGraphic.Item> items = new ArrayList<>();
        for (String label : labels) {
            items.add(new MotionGraphic.Item(label, "", label.toLowerCase(), null));
        }
        return new MotionDirector.Draft(shot, template, "THE CLUES", items);
    }

    @Test
    void aShortCardTakesOverTheNextShotsOfItsSceneAndItemsLandOnTheirWords() {
        ShotList shots = new ShotList(600, List.of(
                shot("s1", "02", "detail", 100, 103),
                shot("s2", "02", "kinetic_text", 103, 106),
                shot("s3", "02", "detail", 106, 109),
                shot("s4", "03", "detail", 109, 112)));
        List<Alignment.Word> said = words(103, "now", "rewind", "it", "airports", "the", "gas", "line", "bob");
        var answer = new MotionDirector.Answer(List.of(draft("s2", "evidence", "Airports", "Gas line", "Bob")));

        ShotList out = MotionDirector.apply(shots, answer, said, Map.of("02", "tense"));

        assertThat(out.shots()).extracting(Shot::id).containsExactly("s1", "s2", "s4");
        Shot animated = out.shots().get(1);
        assertThat(animated.end()).isEqualTo(109);
        assertThat(animated.animation().template()).isEqualTo("evidence");
        assertThat(animated.animation().accent()).isEqualTo("red");
        // "airports" is said at 104.2, "gas line" at 105.0, "bob" at 105.8; each appears just before its word.
        assertThat(animated.animation().items()).extracting(MotionGraphic.Item::at)
                .containsExactly(104.05, 104.85, 105.65);
    }

    @Test
    void theHookIsLeftToTheFilmAndAnimationsKeepTheirDistance() {
        ShotList shots = new ShotList(600, List.of(
                shot("a", "01", "kinetic_text", 5, 11),
                shot("b", "02", "kinetic_text", 100, 106),
                shot("c", "03", "kinetic_text", 150, 156),
                shot("d", "04", "kinetic_text", 260, 266)));
        var answer = new MotionDirector.Answer(List.of(
                draft("a", "chain", "One", "Two", "Three"),
                draft("b", "chain", "One", "Two", "Three"),
                draft("c", "chain", "One", "Two", "Three"),
                draft("d", "chain", "One", "Two", "Three")));

        ShotList out = MotionDirector.apply(shots, answer, List.of(), Map.of());

        assertThat(out.shots().stream().filter(s -> s.animation() != null).map(Shot::id))
                .containsExactly("b", "d");
        // Nothing said matched: the items spread over the shot, in order.
        assertThat(out.shots().get(1).animation().items()).extracting(MotionGraphic.Item::at)
                .isSorted().allMatch(at -> at > 100 && at < 106);
    }

    @Test
    void aFilmShotCanBecomeTheEvidenceBoardAndItsLastClueGetsTimeToBeRead() {
        ShotList shots = new ShotList(600, List.of(
                shot("f1", "08", "detail", 500, 503),
                shot("f2", "08", "character", 503, 505),
                shot("f3", "08", "detail", 505, 508)));
        // The last clue is said near the end of the first two shots: the board takes the third one too.
        List<Alignment.Word> said = words(502.0, "now", "rewind", "airports", "gas", "line", "the", "father", "left");
        var answer = new MotionDirector.Answer(List.of(draft("f1", "evidence", "Airports", "Gas line", "Father")));

        ShotList out = MotionDirector.apply(shots, answer, said, Map.of());

        assertThat(out.shots()).hasSize(1);
        Shot board = out.shots().get(0);
        assertThat(board.type()).isEqualTo("kinetic_text");
        assertThat(board.prompt()).isNull();
        assertThat(board.text()).isEqualTo("THE CLUES");
        assertThat(board.end()).isEqualTo(508);
        double lastAt = board.animation().items().get(2).at();
        assertThat(board.end() - lastAt).isGreaterThanOrEqualTo(MotionDirector.READ_SECONDS);
    }

    @Test
    void itemsStayInOrderWhenTheLastOneIsPulledEarlierToBeRead() {
        // Run 166: "bigger than the last" is said at the very end of the shot, which cannot be lengthened.
        ShotList shots = new ShotList(600, List.of(shot("c", "07", "kinetic_text", 378, 383)));
        List<Alignment.Word> said = words(380.8, "one", "painkiller", "stronger", "wear", "off", "bigger");
        var answer = new MotionDirector.Answer(List.of(draft("c", "chain", "Painkiller", "Stronger", "Wear off",
                "Bigger")));

        MotionGraphic graphic = MotionDirector.apply(shots, answer, said, Map.of()).shots().get(0).animation();

        assertThat(graphic.items()).extracting(MotionGraphic.Item::at).isSorted();
        assertThat(383 - graphic.items().get(3).at()).isGreaterThanOrEqualTo(MotionDirector.READ_SECONDS - 0.01);
    }

    @Test
    void anUnknownTemplateOrTooFewItemsKeepsThePlainCard() {
        assertThat(MotionDirector.clean(draft("s", "spiral", "A", "B", "C"))).isNull();
        assertThat(MotionDirector.clean(draft("s", "versus", "Only one"))).isNull();
        MotionGraphic cut = MotionDirector.clean(draft("s", "list", "A rule that is far too long to fit on one row",
                "Two", "Three", "Four", "Five", "Six"));
        assertThat(cut.items()).hasSize(5);
        assertThat(cut.items().get(0).label()).hasSizeLessThanOrEqualTo(MotionGraphic.MAX_LABEL);
    }

    @Test
    void aboutOneAnimationEveryTwoMinutes() {
        assertThat(MotionDirector.wanted(600)).isEqualTo(5);
        assertThat(MotionDirector.wanted(480)).isEqualTo(4);
        assertThat(MotionDirector.wanted(1500)).isEqualTo(6);
    }
}
