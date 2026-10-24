package com.storyforge.orchestrator.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.script.SfmParser;
import com.storyforge.orchestrator.stage.worker.NarrateStageTestAccess;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PublishKitRulesTest {

    @Test
    void tagsStayUnderYoutubesFiveHundredCharacterLimitWithoutDuplicates() {
        List<String> raw = new ArrayList<>(List.of("#Mystery", "mystery", "Dyatlov Pass", "cold, war"));
        for (int i = 0; i < 60; i++) {
            raw.add("long tag number " + i);
        }

        List<String> tags = TagFitter.fit(raw);

        assertThat(tags).startsWith("Mystery", "Dyatlov Pass", "cold war");
        int cost = tags.stream().mapToInt(t -> t.length() + (t.contains(" ") ? 2 : 0)).sum() + tags.size() - 1;
        assertThat(cost).isLessThanOrEqualTo(TagFitter.MAX_TOTAL_CHARS);
    }

    @Test
    void chaptersFollowYoutubeRules() {
        assertThat(DescriptionBuilder.youtubeChapters(List.of(new EdlChapter(0, "A"), new EdlChapter(60, "B"))))
                .isEmpty();
        List<EdlChapter> chapters = DescriptionBuilder.youtubeChapters(List.of(new EdlChapter(1.2, "Open"),
                new EdlChapter(5, "Too close"), new EdlChapter(40, "Middle"), new EdlChapter(90, "End")));
        assertThat(chapters).extracting(EdlChapter::title).containsExactly("Open", "Middle", "End");
        assertThat(chapters.get(0).start()).isZero();
        String description = DescriptionBuilder.build("Hook.", chapters, List.of(), List.of(),
                List.of("#Dyatlov Pass", "History"));
        assertThat(description).contains("0:00 Open", "1:30 End").endsWith("#DyatlovPass #History\n");
    }

    @Test
    void shortsStartAtStrongBeatsStayUnderTheLimitAndDoNotOverlap() {
        List<Scene> scenes = SfmParser.parse("""
                ## SCENE 01 | beat: cold_open
                Opening line.
                ## SCENE 02 | beat: promise
                Promise line.
                ## SCENE 03 | beat: midpoint_twist
                Twist line.
                ## SCENE 04 | beat: climax
                Climax line.
                """);
        Alignment alignment = new Alignment("t", 160,
                List.of(new Word("Opening", 1, 2, "01"), new Word("line.", 2, 3, "01")),
                List.of(new SceneSpan("01", 0, 30), new SceneSpan("02", 30, 60), new SceneSpan("03", 60, 110),
                        new SceneSpan("04", 110, 160)));

        List<ShortsPlanner.Moment> moments = ShortsPlanner.pick(scenes, alignment, 3, 55);

        assertThat(moments).extracting(ShortsPlanner.Moment::beat).containsExactly("cold_open", "midpoint_twist",
                "climax");
        assertThat(moments).allSatisfy(m -> assertThat(m.length()).isLessThanOrEqualTo(52.5));

        Edl edl = new Edl(2, 1920, 1080, 30, 160, null,
                List.of(new EdlShot("s1", "01", "establishing", 0, 20, "assets/s1.png", "push_in", null, "cut", "ai", null, null, null),
                        new EdlShot("s2", "01", "kinetic_text", 20, 30, "assets/s2.png", "none", "X", "cut", "ai", null, null, null)),
                List.of(), List.of(), List.of(), new Edl.EdlStyle(List.of(), List.of("#000", "#111", "#c9a46b"), false),
                List.of(), null);
        Map<String, Object> spec = ShortsPlanner.spec(moments.get(0), edl, alignment, "Hook", "audio_mix/mix.wav");
        assertThat(spec.get("durationSec")).isEqualTo(32.5);
        assertThat((List<?>) spec.get("shots")).hasSize(1);
        assertThat((List<?>) spec.get("words")).hasSize(2);
    }

    @Test
    void timeStretchScalesWordTimings() {
        ClipAlignment clip = new ClipAlignment(List.of(new ClipAlignment.ClipWord("a", 1.0, 2.0)), "a", 0, 10);
        ClipAlignment faster = NarrateStageTestAccess.scale(clip, 1.25);
        assertThat(faster.words().get(0).start()).isEqualTo(0.8);
        assertThat(faster.durationSec()).isEqualTo(8.0);
    }

    @Test
    void exportFolderNamesAreSafe() {
        assertThat(PublishKitBuilder.slug("Dyatlov Pass: Nine Hikers, One Night!")).isEqualTo(
                "dyatlov-pass-nine-hikers-one-night");
        assertThat(PublishKitBuilder.slug("Café Mystère")).isEqualTo("cafe-mystere");
    }
}
