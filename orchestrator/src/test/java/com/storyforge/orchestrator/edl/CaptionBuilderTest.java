package com.storyforge.orchestrator.edl;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlCaption;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptionBuilderTest {

    @Test
    void breaksOnSentencesLengthAndScenes() {
        List<Word> words = List.of(
                new Word("Short.", 0.0, 0.4, "01"),
                new Word("This", 0.5, 0.8, "01"),
                new Word("line", 0.9, 1.2, "01"),
                new Word("continues", 1.3, 1.8, "01"),
                new Word("into", 1.9, 2.1, "02"),
                new Word("scene", 2.2, 2.5, "02"),
                new Word("two.", 2.6, 2.9, "02"));

        List<EdlCaption> captions = CaptionBuilder.build(words, 42, 3.5);

        assertThat(captions).extracting(EdlCaption::text)
                .containsExactly("Short.", "This line continues", "into scene two.");
        assertThat(captions.get(1).start()).isEqualTo(0.5);
        assertThat(captions.get(1).end()).isEqualTo(1.8);
    }

    @Test
    void writesSrt() {
        String srt = CaptionBuilder.toSrt(List.of(new EdlCaption(0, 1.25, "Hello."),
                new EdlCaption(3661.5, 3662, "Later.")));

        assertThat(srt).isEqualTo("""
                1
                00:00:00,000 --> 00:00:01,250
                Hello.

                2
                01:01:01,500 --> 01:01:02,000
                Later.

                """);
    }
}
