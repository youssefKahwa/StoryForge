package com.storyforge.orchestrator.stage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class StageUsage {

    public record Call(String provider, long inputTokens, long outputTokens, BigDecimal costUsd) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final Set<String> providers = new LinkedHashSet<>();
    private String promptVersion;

    public void provider(String provider) {
        providers.add(provider);
    }

    public void call(String provider, long inputTokens, long outputTokens, BigDecimal costUsd) {
        providers.add(provider);
        calls.add(new Call(provider, inputTokens, outputTokens, costUsd));
    }

    public void promptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public List<Call> calls() {
        return calls;
    }

    public String providers() {
        return providers.isEmpty() ? null : String.join(",", providers);
    }

    public String promptVersion() {
        return promptVersion;
    }

    public long inputTokens() {
        return calls.stream().mapToLong(Call::inputTokens).sum();
    }

    public long outputTokens() {
        return calls.stream().mapToLong(Call::outputTokens).sum();
    }

    public BigDecimal costUsd() {
        return calls.stream().map(Call::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
