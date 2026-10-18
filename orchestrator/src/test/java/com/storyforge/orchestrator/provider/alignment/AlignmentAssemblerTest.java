package com.storyforge.orchestrator.provider.alignment;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.ClipAlignment.ClipWord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.script.SfmParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlignmentAssemblerTest {

    @Test
    void shiftsClipWordsByOffsetAndLeadingSilence() {
        List<Scene> scenes = SfmParser.parse("## SCENE 01 | beat: a\nHello there.\n## SCENE 02 | beat: b\nGoodbye.\n");
        NarrationManifest narration = new NarrationManifest("kokoro", "af_heart", 4.0, "narrate/narration.wav", List.of(
                new SceneAudio("01", "narrate/scene_01.wav", 0.0, 2.2, 200, 0),
                new SceneAudio("02", "narrate/scene_02.wav", 2.2, 1.8, 300, 500)), 1.0);
        Map<String, ClipAlignment> clips = Map.of(
                "01", new ClipAlignment(List.of(new ClipWord("Hello", 0.1, 0.5), new ClipWord("there.", 0.6, 1.0)),
                        "Hello there.", 0, 1.9),
                "02", new ClipAlignment(List.of(new ClipWord("Goodbye.", 0.0, 9.0)), "Goodbye.", 0, 1.0));

        Alignment alignment = AlignmentAssembler.assemble("whisper", scenes, narration, clips);

        assertThat(alignment.words()).extracting(Alignment.Word::start).containsExactly(0.3, 0.8, 2.5);
        assertThat(alignment.words().get(2).end()).isEqualTo(4.0);
        assertThat(alignment.scenes()).extracting(Alignment.SceneSpan::end).containsExactly(2.2, 4.0);
    }
}
