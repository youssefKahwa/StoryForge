package com.storyforge.orchestrator.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmRouterTest {

    @Test
    void extractsJsonFromFencesAndChatter() {
        assertThat(LlmRouter.extractJson("Sure! Here it is:\n```json\n{\"a\": 1}\n```\nEnjoy."))
                .isEqualTo("{\"a\": 1}");
        assertThat(LlmRouter.extractJson("prefix {\"a\": {\"b\": 2}} suffix")).isEqualTo("{\"a\": {\"b\": 2}}");
        assertThat(LlmRouter.extractJson("[1, 2]")).isEqualTo("[1, 2]");
    }
}
