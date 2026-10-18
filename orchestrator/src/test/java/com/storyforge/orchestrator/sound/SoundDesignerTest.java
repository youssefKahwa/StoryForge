package com.storyforge.orchestrator.sound;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.script.SfmParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SoundDesignerTest {

    private static final LicenseRecord LICENSE = new LicenseRecord("licensed", "test", null, "test", null);

    @Test
    void mapsFreeTextMoodsAndCuesToPresets() {
        assertThat(MusicLibrary.canonicalMood("ominous")).isEqualTo("dread");
        assertThat(MusicLibrary.canonicalMood("Somber reflection")).isEqualTo("sad");
        assertThat(MusicLibrary.canonicalMood("epic")).isEqualTo("epic");
        assertThat(SoundDesigner.preset("low_wind_creak")).isEqualTo("wind");
        assertThat(SoundDesigner.preset("deep impact")).isEqualTo("low_hit");
        assertThat(SoundDesigner.preset("applause")).isNull();
    }

    @Test
    void groupsScenesIntoMoodSegmentsAndPlacesCuesOnAnchorWords() {
        List<Scene> scenes = SfmParser.parse("""
                ## SCENE 01 | beat: a
                The signal stopped at dawn.
                @music: mood=ominous, intensity=0.5
                @sfx: low_hit @ "stopped"
                ## SCENE 02 | beat: b
                Nobody knew yet.
                ## SCENE 03 | beat: c
                Years later, hope returned.
                @music: mood=hopeful, intensity=1
                """);
        Alignment alignment = new Alignment("test", 90, List.of(
                new Word("The", 0.2, 0.4, "01"), new Word("signal", 0.5, 0.9, "01"),
                new Word("stopped", 1.0, 1.5, "01"), new Word("Years", 60.2, 60.6, "03")),
                List.of(new SceneSpan("01", 0, 30), new SceneSpan("02", 30, 60), new SceneSpan("03", 60, 90)));
        Map<String, List<MusicLibrary.Track>> library = Map.of(
                "dread", List.of(new MusicLibrary.Track("dread", Path.of("d.mp3"), "dread/d.mp3", LICENSE)),
                "hope", List.of(new MusicLibrary.Track("hope", Path.of("h.mp3"), "hope/h.mp3", LICENSE)));

        SoundPlan plan = SoundDesigner.plan(scenes, alignment, List.of(new EdlChapter(0, "One"),
                new EdlChapter(60, "Two")), library, 1, preset -> preset + ".wav", 0);

        assertThat(plan.music()).extracting(SoundPlan.MusicSegment::mood).containsExactly("dread", "hope");
        assertThat(plan.music().get(0).end()).isEqualTo(60.75);
        assertThat(plan.music().get(1).gainDb()).isEqualTo(-14.0);
        assertThat(plan.sfx()).extracting(SoundPlan.SfxCue::preset).containsExactly("low_hit", "whoosh");
        assertThat(plan.sfx().get(0).start()).isEqualTo(1.0);
        assertThat(plan.sfx().get(1).start()).isEqualTo(59.55);
    }
}
