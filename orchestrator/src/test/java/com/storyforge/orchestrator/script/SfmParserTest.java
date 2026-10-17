package com.storyforge.orchestrator.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyforge.orchestrator.model.Scene;
import java.util.List;
import org.junit.jupiter.api.Test;

class SfmParserTest {

    private static final String SPEC_EXAMPLE = """
            ## SCENE 07 | beat: escalation_2 | duration_target: 38s
            [VOICE: narrator_main] [TONE: tense] [SPEED: 0.95] [SILENCE_BEFORE: 300ms]
            By sunrise, the caravan had lost two men — and the desert was only getting started.
            [PAUSE: 600ms]
            [EMPHASIS] Nobody [/EMPHASIS] in Marrakech expected them to come back.
            [SILENCE_AFTER: 400ms]
            @visual: wide shot, endless dunes at dawn, tiny caravan silhouette, dust in the wind
            @sfx: wind_gust_low @ "desert"
            @music: mood=dread, intensity=0.6
            @onscreen: "Day 3 — 2 men lost"
            @claims: c3, c4
            """;

    @Test
    void parsesTheSpecExample() {
        List<Scene> scenes = SfmParser.parse(SPEC_EXAMPLE);

        assertThat(scenes).hasSize(1);
        Scene scene = scenes.get(0);
        assertThat(scene.id()).isEqualTo("07");
        assertThat(scene.beat()).isEqualTo("escalation_2");
        assertThat(scene.durationTargetSec()).isEqualTo(38.0);
        assertThat(scene.voice()).isEqualTo("narrator_main");
        assertThat(scene.tone()).isEqualTo("tense");
        assertThat(scene.speed()).isEqualTo(0.95);
        assertThat(scene.silenceBeforeMs()).isEqualTo(300);
        assertThat(scene.silenceAfterMs()).isEqualTo(400);
        assertThat(scene.narration()).isEqualTo("By sunrise, the caravan had lost two men — and the desert was only "
                + "getting started. Nobody in Marrakech expected them to come back.");
        assertThat(scene.visual()).startsWith("wide shot, endless dunes");
        assertThat(scene.sfx()).containsExactly(new Scene.SfxCue("wind_gust_low", "desert"));
        assertThat(scene.music()).isEqualTo(new Scene.MusicCue("dread", 0.6));
        assertThat(scene.onscreen()).isEqualTo("Day 3 — 2 men lost");
        assertThat(scene.claims()).containsExactly("c3", "c4");
        assertThat(scene.warnings()).isEmpty();
    }

    @Test
    void keepsPausesAndEmphasisAsSegments() {
        Scene scene = SfmParser.parse(SPEC_EXAMPLE).get(0);

        assertThat(scene.segments()).extracting(Scene.Segment::kind)
                .containsExactly("text", "pause", "text", "text");
        assertThat(scene.segments().get(1).pauseMs()).isEqualTo(600);
        assertThat(scene.segments().get(2)).isEqualTo(Scene.Segment.text("Nobody", true));
        assertThat(scene.segments().get(3).emphasis()).isFalse();
    }

    @Test
    void mergesStandaloneDashesIntoThePreviousWord() {
        Scene scene = SfmParser.parse(SPEC_EXAMPLE).get(0);

        assertThat(scene.words()).contains("men—").doesNotContain("—");
    }

    @Test
    void parsesMultipleScenesAndIgnoresPreamble() {
        List<Scene> scenes = SfmParser.parse("""
                # Title line is ignored
                Text before the first scene is ignored.
                ## SCENE 01 | beat: cold_open
                First.
                ## SCENE 02 | beat: promise
                Second.
                """);

        assertThat(scenes).extracting(Scene::id).containsExactly("01", "02");
        assertThat(scenes).extracting(Scene::index).containsExactly(0, 1);
        assertThat(scenes.get(1).narration()).isEqualTo("Second.");
    }

    @Test
    void recordsUnknownTagsAsWarningsInsteadOfFailing() {
        Scene scene = SfmParser.parse("## SCENE 01 | beat: x\n[WHISPER] hello [FOO: bar]\n@colour: red\n").get(0);

        assertThat(scene.narration()).isEqualTo("hello");
        assertThat(scene.warnings()).hasSize(3);
    }

    @Test
    void rejectsMarkupWithoutScenesOrWithDuplicateIds() {
        assertThatThrownBy(() -> SfmParser.parse("just prose")).isInstanceOf(SfmParseException.class);
        assertThatThrownBy(() -> SfmParser.parse("## SCENE 01\na\n## SCENE 01\nb"))
                .isInstanceOf(SfmParseException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void parsesDurations() {
        assertThat(SfmParser.parseMillis("300ms")).isEqualTo(300);
        assertThat(SfmParser.parseMillis("1.5s")).isEqualTo(1500);
        assertThat(SfmParser.parseSeconds("38s")).isEqualTo(38.0);
        assertThat(SfmParser.parseSeconds("500ms")).isEqualTo(0.5);
    }
}
