package com.storyforge.orchestrator.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InspirationIdeasTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsTheBlueprintAndVoiceAndKeepsTheLengthMonetizable() {
        InspirationIdeas ideas = json.readValue("""
                {"topic": "Fight Club", "story": "s", "leads": [], "whatWorked": [],
                 "blueprint": [{"title": "Why it flopped", "points": ["box office", "the DVD comeback"], "share": 0.2}],
                 "voice": {"persona": "a fan", "energy": "high", "humor": "dry", "viewer": "you",
                           "devices": ["ratings"]},
                 "angles": [], "suggestedFormat": "FILM_ANALYSIS", "suggestedMinutes": 6, "cautions": []}
                """, InspirationIdeas.class);

        assertThat(ideas.hasBlueprint()).isTrue();
        assertThat(ideas.blueprint().get(0).points()).containsExactly("box office", "the DVD comeback");
        assertThat(ideas.voice().persona()).isEqualTo("a fan");
        assertThat(ideas.suggestedMinutes()).isEqualTo(8);
    }

    @Test
    void analysesFromBeforeTheBlueprintStillLoad() {
        InspirationIdeas ideas = json.readValue("""
                {"topic": "t", "story": "s", "leads": [], "whatWorked": [], "angles": [],
                 "suggestedFormat": "GREAT_STORIES", "suggestedMinutes": 16, "cautions": []}
                """, InspirationIdeas.class);

        assertThat(ideas.hasBlueprint()).isFalse();
        assertThat(ideas.voice()).isNull();
        assertThat(ideas.suggestedMinutes()).isEqualTo(12);
    }
}
