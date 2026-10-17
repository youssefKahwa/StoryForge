package com.storyforge.orchestrator.visual;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.model.ShotType;
import com.storyforge.orchestrator.script.SfmParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotPlannerTest {

    private static final ShotPlanner.Rules RULES = new ShotPlanner.Rules(3, 6, 3, 60);
    private static final FormatDefinition.Style STYLE =
            new FormatDefinition.Style("art", "painterly", "no text", List.of(), List.of(), false);

    @Test
    void producesContiguousShotsWithinDurationLimits() {
        Fixture fixture = fixture(12, 28);
        ShotList plan = new ShotPlanner(RULES).plan(fixture.scenes, fixture.alignment, STYLE, 1000);

        List<Shot> shots = plan.shots();
        assertThat(shots.get(0).start()).isEqualTo(0.0);
        assertThat(shots.get(shots.size() - 1).end()).isCloseTo(plan.durationSec(), org.assertj.core.data.Offset.offset(0.001));
        for (int i = 1; i < shots.size(); i++) {
            assertThat(shots.get(i).start()).isEqualTo(shots.get(i - 1).end());
        }
        assertThat(shots).allSatisfy(shot -> assertThat(shot.duration()).isBetween(2.999, 6.001));
    }

    @Test
    void neverRepeatsAShotTypeMoreThanThreeTimesInARow() {
        Fixture fixture = fixture(20, 30);
        List<Shot> shots = new ShotPlanner(RULES).plan(fixture.scenes, fixture.alignment, STYLE, 1).shots();

        int run = 1;
        for (int i = 1; i < shots.size(); i++) {
            run = shots.get(i).type().equals(shots.get(i - 1).type()) ? run + 1 : 1;
            assertThat(run).isLessThanOrEqualTo(3);
        }
    }

    @Test
    void placesAPatternInterruptAtLeastEverySixtySeconds() {
        Fixture fixture = fixture(30, 30);
        ShotList plan = new ShotPlanner(RULES).plan(fixture.scenes, fixture.alignment, STYLE, 1);

        double last = 0;
        for (Shot shot : plan.shots()) {
            if (ShotType.isInterrupt(shot.type())) {
                assertThat(shot.start() - last).isLessThanOrEqualTo(60);
                last = shot.start();
            }
        }
        assertThat(plan.durationSec() - last).isLessThanOrEqualTo(60);
    }

    @Test
    void cutsOnPhraseBoundariesAndUsesOnscreenTextForKineticShots() {
        Fixture fixture = fixture(2, 20);
        List<Shot> shots = new ShotPlanner(RULES).plan(fixture.scenes, fixture.alignment, STYLE, 1).shots();

        assertThat(shots.get(0).type()).isEqualTo(ShotType.ESTABLISHING);
        assertThat(shots).filteredOn(shot -> ShotType.KINETIC_TEXT.equals(shot.type()))
                .extracting(Shot::text).contains("On screen 1");
        assertThat(shots).filteredOn(shot -> !shot.graphic())
                .allSatisfy(shot -> assertThat(shot.prompt()).startsWith("painterly"));
        List<Double> phraseEnds = new ArrayList<>();
        for (Word word : fixture.alignment.words()) {
            if (word.endsPhrase()) {
                phraseEnds.add(word.end());
            }
        }
        long cutsAfterPhrase = shots.stream().skip(1).filter(shot -> phraseEnds.stream()
                .anyMatch(end -> shot.start() > end && shot.start() - end < 0.2)).count();
        assertThat(cutsAfterPhrase).isGreaterThan(0);
    }

    private record Fixture(List<Scene> scenes, Alignment alignment) {
    }

    private static Fixture fixture(int sceneCount, int wordsPerScene) {
        StringBuilder markup = new StringBuilder();
        for (int s = 0; s < sceneCount; s++) {
            markup.append("## SCENE %02d | beat: beat_%d%n".formatted(s + 1, s / 2));
            StringBuilder text = new StringBuilder();
            for (int w = 0; w < wordsPerScene; w++) {
                text.append("word").append(w).append(w % 5 == 4 ? ", " : " ");
            }
            markup.append(text.toString().strip()).append(".\n");
            if (s % 3 == 1) {
                markup.append("@onscreen: \"On screen ").append(s).append("\"\n");
            }
            markup.append("@visual: scene ").append(s).append('\n');
        }
        List<Scene> scenes = SfmParser.parse(markup.toString());
        List<Word> words = new ArrayList<>();
        List<SceneSpan> spans = new ArrayList<>();
        double cursor = 0;
        for (Scene scene : scenes) {
            double start = cursor;
            cursor += 0.2;
            for (String token : scene.words()) {
                words.add(new Word(token, cursor, cursor + 0.32, scene.id()));
                cursor += 0.4;
            }
            cursor += 0.2;
            spans.add(new SceneSpan(scene.id(), start, cursor));
        }
        return new Fixture(scenes, new Alignment("test", cursor, words, spans));
    }
}
