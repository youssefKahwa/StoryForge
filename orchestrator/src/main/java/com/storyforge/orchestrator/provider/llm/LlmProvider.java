package com.storyforge.orchestrator.provider.llm;

public interface LlmProvider {

    String name();

    LlmResponse complete(LlmRequest request);
}
