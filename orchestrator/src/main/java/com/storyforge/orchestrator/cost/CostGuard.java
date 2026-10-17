package com.storyforge.orchestrator.cost;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageBlockedException;
import com.storyforge.orchestrator.stage.StageUsage;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CostGuard {

    private final CostEntryRepository repository;
    private final BigDecimal cap;

    public CostGuard(CostEntryRepository repository, StoryForgeProperties properties) {
        this.repository = repository;
        this.cap = properties.budget().maxUsdPerVideo();
    }

    @Transactional(readOnly = true)
    public BigDecimal spent(long runId) {
        return repository.totalForRun(runId);
    }

    @Transactional(readOnly = true)
    public void ensureWithinBudget(long runId, BigDecimal estimatedUsd) {
        BigDecimal projected = spent(runId).add(estimatedUsd);
        if (projected.compareTo(cap) > 0) {
            throw new StageBlockedException("Budget cap $" + cap + " would be exceeded (projected $" + projected + ")");
        }
    }

    @Transactional
    public void record(long runId, Stage stage, StageUsage usage) {
        usage.calls().forEach(call -> repository.save(new CostEntry(runId, stage, call.provider(),
                call.inputTokens(), call.outputTokens(), call.costUsd())));
    }
}
