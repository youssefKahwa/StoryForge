package com.storyforge.orchestrator.host;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HostMomentsTest {

    @Test
    void picksTheLinesWhereTheNarratorSpeaksAsAPersonSpreadOutAndNeverInTheHook() {
        List<String> lines = List.of(
                "Honestly, I think this is the best scene.",       // 0 s: hook, too early
                "The narrator works in an office.",                // 4 s: plain narration
                "And honestly, my favorite detail is the soap.",   // 8 s: first person
                "Tyler sells soap to rich people.",                // 12 s
                "Did you notice the single frames?",               // 16 s: question to the viewer, too close
                "The building falls.",                             // 20 s
                "Tell me in the comments what you think.");        // 50 s: call to action
        List<Shot> shots = new ArrayList<>();
        List<Word> words = new ArrayList<>();
        double[] starts = {0, 4, 8, 12, 16, 20, 50};
        for (int i = 0; i < lines.size(); i++) {
            double start = starts[i];
            shots.add(new Shot("shot_%03d".formatted(i + 1), "01", "detail", start, start + 3.5, "p", "push_in",
                    null, i));
            String[] tokens = lines.get(i).split(" ");
            for (int w = 0; w < tokens.length; w++) {
                words.add(new Word(tokens[w], start + w * 0.3, start + w * 0.3 + 0.25, "01"));
            }
        }
        Alignment alignment = new Alignment("test", 120, words, List.of());

        List<HostMoments.Moment> moments = HostMoments.pick(new ShotList(120, shots), alignment, Set.of());

        assertThat(moments).extracting(HostMoments.Moment::shotId).containsExactly("shot_003", "shot_007");
        assertThat(moments.get(1).intent()).isEqualTo("cta");
        assertThat(moments.stream().mapToDouble(HostMoments.Moment::length).sum()).isLessThanOrEqualTo(12);
    }

    @Test
    void footageShotsAreNeverTakenAndTheIntentFollowsTheWords() {
        assertThat(HostMoments.intent("here's the real reason")).isEqualTo("secret");
        assertThat(HostMoments.intent("why would he do that")).isEqualTo("realize");
        assertThat(HostMoments.intent("would you join?")).isEqualTo("question");
        assertThat(HostMoments.intent("and then the twist hits")).isEqualTo("shock");
        assertThat(HostMoments.score("the building falls.")).isZero();
        assertThat(HostMoments.score("did you see it?")).isGreaterThan(HostMoments.score("did it fall?"));
    }

    @Test
    void cutsLandBetweenWordsNearEvenSplits() {
        HostMoments.Moment moment = new HostMoments.Moment("shot_001", 10, 15, "explain");
        List<Word> words = List.of(new Word("one", 10.2, 11.5, "01"), new Word("two", 11.6, 11.9, "01"),
                new Word("three", 12.0, 13.4, "01"), new Word("four", 13.5, 14.8, "01"));
        Alignment alignment = new Alignment("test", 20, words, List.of());

        List<Double> cuts = CharacterHost.cutPoints(moment, alignment);

        assertThat(cuts).hasSize(2);
        assertThat(cuts.get(0)).isEqualTo(1.5, org.assertj.core.data.Offset.offset(0.001));
        assertThat(cuts.get(1)).isEqualTo(3.4, org.assertj.core.data.Offset.offset(0.001));
    }
}
