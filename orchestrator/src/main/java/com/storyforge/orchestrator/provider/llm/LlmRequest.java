package com.storyforge.orchestrator.provider.llm;

import com.storyforge.orchestrator.stage.Stage;
import java.util.Map;

public record LlmRequest(Stage stage, String promptVersion, String system, String user, String tier,
                         Map<String, String> vars) {
}
