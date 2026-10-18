package com.storyforge.orchestrator.provider.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storyforge.orchestrator.TestProperties;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ClaudeCodeLlmProviderTest {

    private static final String RECORDED = """
            {"type":"result","subtype":"success","is_error":false,"duration_ms":1659,"num_turns":1,
             "result":"```json\\n{\\"ok\\": true}\\n```","total_cost_usd":0.002214,
             "usage":{"input_tokens":602,"cache_creation_input_tokens":10,"cache_read_input_tokens":5,
                      "output_tokens":129}}
            """;

    private final ClaudeCodeLlmProvider provider = provider(true);

    @Test
    void parsesARecordedHeadlessResult() {
        LlmResponse response = provider.parse(RECORDED, "haiku", true, 1700);

        assertThat(response.text()).contains("\"ok\": true");
        assertThat(response.inputTokens()).isEqualTo(617);
        assertThat(response.outputTokens()).isEqualTo(129);
        assertThat(response.costUsd()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(LlmRouter.extractJson(response.text())).isEqualTo("{\"ok\": true}");
    }

    @Test
    void reportsNotionalCostWhenNotOnASubscription() {
        assertThat(provider.parse(RECORDED, "haiku", false, 0).costUsd()).isEqualByComparingTo("0.002214");
    }

    @Test
    void rejectsErrorResults() {
        String error = "{\"type\":\"result\",\"subtype\":\"error_max_turns\",\"is_error\":true,\"result\":\"boom\"}";
        assertThatThrownBy(() -> provider.parse(error, "haiku", true, 0))
                .isInstanceOf(LlmException.class).hasMessageContaining("error_max_turns");
    }

    @Test
    void buildsALeastPrivilegeCommand() {
        List<String> pure = provider.command("haiku", "low", "", Path.of("sys.md"));
        assertThat(pure).containsSequence("--tools", "").containsSequence("--effort", "low")
                .contains("--safe-mode", "--strict-mcp-config")
                .doesNotContain("--allowedTools", "--dangerously-skip-permissions");

        List<String> research = provider.command("sonnet", null, "WebSearch,WebFetch", Path.of("sys.md"));
        assertThat(research).containsSequence("--allowedTools", "WebSearch,WebFetch");
    }

    private static ClaudeCodeLlmProvider provider(boolean subscription) {
        StoryForgeProperties.Llm.ClaudeCode claude = new StoryForgeProperties.Llm.ClaudeCode(true, "claude",
                "target/claude-test", 60, subscription, Map.of("fast", "haiku", "high", "sonnet"), Map.of(), Map.of());
        StoryForgeProperties.Llm llm = new StoryForgeProperties.Llm(true, "", List.of("claude-code"), Map.of(), 2, 2,
                claude);
        StoryForgeProperties properties = TestProperties.withLlm(llm);
        return new ClaudeCodeLlmProvider(properties, JsonMapper.builder().build());
    }
}
