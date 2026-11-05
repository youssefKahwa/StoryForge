package com.storyforge.orchestrator.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.HostAppearance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostPlannerTest {

    @Test
    void popsInForTheOpeningQuestionsAndTheOutroWithinTheBudget() {
        List<Word> words = new ArrayList<>();
        double t = 0;
        for (int sentence = 0; sentence < 40; sentence++) {
            for (int w = 0; w < 8; w++) {
                String text = "word";
                if (w == 7) {
                    text = sentence % 6 == 2 ? "why?" : "end.";
                }
                words.add(new Word(text, t, t + 0.35, "01"));
                t += 0.4;
            }
            t += 0.3;
        }
        double duration = t + 2;

        List<double[]> ranges = HostPlanner.appearances(words, duration);

        assertThat(ranges).isNotEmpty();
        assertThat(ranges.get(0)[0]).isLessThan(40);
        assertThat(ranges.get(ranges.size() - 1)[1]).isGreaterThan(duration - 1);
        double total = ranges.stream().mapToDouble(r -> r[1] - r[0]).sum();
        assertThat(total).isLessThanOrEqualTo(duration * 0.2 + 9);
        for (int i = 1; i < ranges.size(); i++) {
            assertThat(ranges.get(i)[0]).isGreaterThanOrEqualTo(ranges.get(i - 1)[1]);
        }
    }

    @Test
    void mouthIsClosedInSilenceAndOpensWithTheVoice() {
        double[] openness = {0.5, 0.0, 1.0, 0.25, 0.75};
        double[] db = new double[60];
        for (int f = 0; f < 60; f++) {
            db[f] = f < 30 ? -80 : -12;
        }

        List<HostAppearance> mouths = HostPlanner.mouths(List.<double[]>of(new double[]{0, 2}), db, openness, 30);

        List<Integer> frames = mouths.get(0).frames();
        assertThat(frames).hasSize(60);
        assertThat(frames.subList(0, 30)).containsOnly(1);
        assertThat(frames.subList(35, 60)).doesNotContain(1);
        assertThat(frames.subList(35, 60)).contains(2);
    }

    @Test
    void vectorMouthOpensWithTheVoiceAndClosesInSilence() {
        double[] db = new double[90];
        java.util.Arrays.fill(db, -90);
        for (int f = 30; f < 60; f++) {
            db[f] = -16;
        }
        List<HostAppearance> mouths = HostPlanner.openness(List.<double[]>of(new double[] {0, 3}), db, 30);
        List<Double> open = mouths.get(0).open();
        assertThat(open).hasSize(90);
        assertThat(open.get(10)).isZero();
        assertThat(open.get(45)).isGreaterThan(0.8);
        assertThat(open.get(89)).isLessThan(0.05);
        assertThat(mouths.get(0).shape()).hasSize(90);
    }

    @Test
    void talkingLogoNeverCoversTheMaskedCharacter() {
        List<double[]> ranges = List.of(new double[] {5, 10}, new double[] {100, 106}, new double[] {588, 599.6});
        List<double[]> character = List.of(new double[] {3, 8}, new double[] {106.5, 112});
        List<double[]> kept = HostPlanner.clearOf(ranges, character);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0)[0]).isEqualTo(588);
        assertThat(HostPlanner.clearOf(ranges, List.of())).hasSize(3);
    }
}
