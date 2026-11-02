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

    public synchronized void provider(String provider) {
        providers.add(provider);
    }

    public synchronized void call(String provider, long inputTokens, long outputTokens, BigDecimal costUsd) {
        providers.add(provider);
        calls.add(new Call(provider, inputTokens, outputTokens, costUsd));
    }

    public synchronized void promptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public synchronized List<Call> calls() {
        return List.copyOf(calls);
    }

    public synchronized String providers() {
        return providers.isEmpty() ? null : String.join(",", providers);
    }

    public String promptVersion() {
        return promptVersion;
    }

    public synchronized long inputTokens() {
        return calls.stream().mapToLong(Call::inputTokens).sum();
    }

    public synchronized long outputTokens() {
        return calls.stream().mapToLong(Call::outputTokens).sum();
    }

    public synchronized BigDecimal costUsd() {
        return calls.stream().map(Call::costUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
