package com.storyforge.orchestrator.stage;

import com.storyforge.orchestrator.api.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StageExecutionService {

    private static final int MAX_ERROR = 4000;

    private final StageExecutionRepository repository;

    public StageExecutionService(StageExecutionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StageExecution> forRun(long runId) {
        return repository.findByRunIdOrderByIdAsc(runId);
    }

    @Transactional(readOnly = true)
    public List<StageExecution> forRuns(Collection<Long> runIds) {
        return repository.findByRunIdInOrderByIdAsc(runIds);
    }

    @Transactional(readOnly = true)
    public boolean hasSucceeded(long runId, Stage stage) {
        return repository.existsByRunIdAndStageAndStatus(runId, stage, StageStatus.SUCCEEDED);
    }

    @Transactional
    public long start(long runId, Stage stage) {
        StageExecution execution = new StageExecution();
        execution.setRunId(runId);
        execution.setStage(stage);
        execution.setStatus(StageStatus.RUNNING);
        execution.setStartedAt(Instant.now());
        return repository.save(execution).getId();
    }

    @Transactional
    public void finish(long executionId, StageStatus status, StageUsage usage, String error) {
        StageExecution execution = repository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Stage execution " + executionId + " not found"));
        Instant now = Instant.now();
        execution.setStatus(status);
        execution.setFinishedAt(now);
        execution.setDurationMs(Duration.between(execution.getStartedAt(), now).toMillis());
        execution.setProvider(usage.providers());
        execution.setPromptVersion(usage.promptVersion());
        execution.setInputTokens(usage.inputTokens());
        execution.setOutputTokens(usage.outputTokens());
        execution.setCostUsd(usage.costUsd());
        execution.setError(error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR)));
    }
}
