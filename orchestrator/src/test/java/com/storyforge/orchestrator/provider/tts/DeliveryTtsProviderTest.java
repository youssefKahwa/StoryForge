package com.storyforge.orchestrator.provider.tts;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.script.SfmParser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryTtsProviderTest {

    private static final String SCENE = """
            ## SCENE 03 | beat: twist | duration_target: 30s
            [VOICE: narrator_main] [TONE: playful] [SPEED: 1.0]
            [mood: hype] Perfect system, *right*? [beat] Until Marla walks in, back in 1999. [mood: serious] She fakes it.
            @say: Palahniuk = Paula-nick
            @say: Durden = ipa: ˈdɜːrdən
            """;

    @Test
    void theSceneTravelsInScriptOrderWithItsMarks() {
        Scene scene = SfmParser.parse(SCENE).get(0);

        List<Map<String, Object>> items = DeliveryTtsProvider.items(scene);

        assertThat(items).extracting(item -> item.get("kind"))
                .containsExactly("mood", "text", "text", "beat", "text", "mood", "text");
        assertThat(items.get(2).get("text")).isEqualTo("right?");
        assertThat(items.get(4).get("text")).isEqualTo("Until Marla walks in, back in nineteen ninety-nine.");
        assertThat(items.get(5).get("text")).isEqualTo("serious");
        assertThat(scene.narration()).doesNotContain("*").contains("Perfect system, right?");
    }

    @Test
    void toneBecomesTheStartingMood() {
        assertThat(DeliveryTtsProvider.mood("playful", null)).isEqualTo("hype");
        assertThat(DeliveryTtsProvider.mood("tense", null)).isEqualTo("serious");
        assertThat(DeliveryTtsProvider.mood("angry rant", null)).isEqualTo("angry");
        assertThat(DeliveryTtsProvider.mood(null, null)).isEqualTo("neutral");
    }

    @Test
    void sayLinesJoinTheConfiguredPronunciations() {
        Scene scene = SfmParser.parse(SCENE).get(0);
        Map<String, Map<String, String>> base = Map.of("bro", Map.of("say", "bruh"));

        Map<String, Map<String, String>> lexicon = DeliveryTtsProvider.lexicon(base, scene);

        assertThat(lexicon).containsEntry("bro", Map.of("say", "bruh"))
                .containsEntry("palahniuk", Map.of("say", "Paula-nick"))
                .containsEntry("durden", Map.of("ipa", "ˈdɜːrdən"));
    }

    @Test
    void magpieVoicesAreRecognised() {
        assertThat(DeliveryTtsProvider.isMagpie("magpie:Jason")).isTrue();
        assertThat(DeliveryTtsProvider.isMagpie("am_michael:60+am_fenrir:40")).isFalse();
    }
}
