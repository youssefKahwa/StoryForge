package com.storyforge.orchestrator.provider.llm;

import java.math.BigDecimal;

public record LlmResponse(String text, String provider, String model, long inputTokens, long outputTokens,
                          BigDecimal costUsd, long durationMs) {
}
