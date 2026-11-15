package com.storyforge.orchestrator.stage.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.VideoTranscript;
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

    @Test
    void theMainVideoTimelineMarksWhatViewersReplayed() {
        VideoTranscript main = new VideoTranscript("u", "id", "t", "c", 90, "en", "captions", "x",
                List.of(new VideoTranscript.Line(0, 10, "He cannot sleep."),
                        new VideoTranscript.Line(31, 40, "He meets Tyler on a plane."),
                        new VideoTranscript.Line(65, 80, "The first fight.")),
                List.of(new VideoTranscript.Heat(30, 45, 0.9), new VideoTranscript.Heat(60, 90, 0.2)));
        List<String> lines = OutlineStage.timeline(main).lines().toList();
        assertThat(lines).containsExactly(
                "[0:00] He cannot sleep.",
                "[0:30] ★ most replayed He meets Tyler on a plane.",
                "[1:00] The first fight.");
    }

    @Test
    void aStorySpineSurvivesTheBudgetAndJson() {
        String spineJson = """
                {"totalSeconds": 600,
                 "beats": [{"beat": "hook", "targetSeconds": 60, "summary": "s", "points": [],
                            "moment": "He wakes up in a stranger's office", "question": "Who is Tyler?",
                            "turn": "But", "take": "Insomnia is the clue", "loop": "And then the plane lands"}],
                 "spine": {"arc": {"setup": "a", "incident": "b", "escalation": "c", "twist": "d", "ending": "e"},
                           "plants": [{"clue": "the flashes of Tyler", "plantIn": "hook", "payoffIn": "twist"}],
                           "hooks": ["one", "two", "three"], "chosenHook": 1}}
                """;
        // As the app reads it (application.yml): the model leaves wordBudget out, the budget step fills it in.
        Outline draft = tools.jackson.databind.json.JsonMapper.builder()
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build().readValue(spineJson, Outline.class);
        Outline normal = OutlineStage.normalize(draft, 600, 181);
        assertThat(normal.spine()).isNotNull();
        assertThat(normal.spine().plants()).extracting(Outline.Plant::payoffIn).containsExactly("twist");
        assertThat(normal.spine().hooks()).hasSize(3);
        Outline.Beat beat = normal.beats().get(0);
        assertThat(beat.moment()).isEqualTo("He wakes up in a stranger's office");
        assertThat(beat.question()).isEqualTo("Who is Tyler?");
        assertThat(beat.loop()).isEqualTo("And then the plane lands");
        assertThat(beat.wordBudget()).isPositive();
    }
}
