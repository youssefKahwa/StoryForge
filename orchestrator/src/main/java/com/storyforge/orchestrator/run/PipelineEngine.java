package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.cost.CostGuard;
import com.storyforge.orchestrator.notify.NotificationService;
import com.storyforge.orchestrator.settings.SettingsService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageBlockedException;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageExecutionService;
import com.storyforge.orchestrator.stage.StageStatus;
import com.storyforge.orchestrator.stage.StageUsage;
import com.storyforge.orchestrator.stage.StageWorker;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    private final RunService runs;
    private final StageExecutionService executions;
    private final ArtifactStore artifacts;
    private final SettingsService settings;
    private final CostGuard costGuard;
    private final NotificationService notifications;
    private final Map<Stage, StageWorker> workers = new EnumMap<>(Stage.class);

    public PipelineEngine(RunService runs, StageExecutionService executions, ArtifactStore artifacts,
                          SettingsService settings, CostGuard costGuard, NotificationService notifications,
                          List<StageWorker> workers) {
        this.notifications = notifications;
        this.runs = runs;
        this.executions = executions;
        this.artifacts = artifacts;
        this.settings = settings;
        this.costGuard = costGuard;
        workers.forEach(worker -> this.workers.put(worker.stage(), worker));
        for (Stage stage : Stage.values()) {
            if (!this.workers.containsKey(stage)) {
                throw new IllegalStateException("No worker registered for stage " + stage);
            }
        }
    }

    public void execute(long runId) {
        while (true) {
            Run run = runs.get(runId);
            if (run.getStatus() != RunStatus.RUNNING) {
                return;
            }
            if (settings.isPaused()) {
                runs.requeue(runId, "Paused by kill switch");
                return;
            }
            Optional<Stage> next = Stage.after(run.getState());
            if (next.isEmpty()) {
                runs.complete(runId);
                notifications.send("StoryForge: run #" + runId + " is ready (" + run.getTopicTitle() + "). "
                        + "Uploaded as private; review it and veto before the publish time if needed.\n"
                        + notifications.runLink(runId));
                return;
            }
            if (!runStage(RunInfo.from(run), next.get())) {
                return;
            }
        }
    }

    private boolean runStage(RunInfo run, Stage stage) {
        StageWorker worker = workers.get(stage);
        RunFiles files = artifacts.forRun(run.id());
        StageUsage usage = new StageUsage();
        long executionId = executions.start(run.id(), stage);

        if (executions.hasSucceeded(run.id(), stage) && files.hasAll(stage, worker.outputs())) {
            executions.finish(executionId, StageStatus.CACHED, usage, null);
            runs.advance(run.id(), stage.to());
            return true;
        }

        try {
            costGuard.ensureWithinBudget(run.id(), BigDecimal.ZERO);
            log.info("Run {} stage {} started", run.id(), stage);
            worker.execute(new StageContext(run, files, usage));
            costGuard.record(run.id(), stage, usage);
            executions.finish(executionId, StageStatus.SUCCEEDED, usage, null);
            runs.advance(run.id(), stage.to());
            log.info("Run {} stage {} succeeded", run.id(), stage);
            return true;
        } catch (StageBlockedException e) {
            costGuard.record(run.id(), stage, usage);
            executions.finish(executionId, StageStatus.BLOCKED, usage, e.getMessage());
            runs.block(run.id(), stage, e.getMessage());
            notifications.send("StoryForge: run #" + run.id() + " is blocked at " + stage.folder() + ": "
                    + e.getMessage() + "\n" + notifications.runLink(run.id()));
            log.warn("Run {} stage {} blocked: {}", run.id(), stage, e.getMessage());
        } catch (Exception e) {
            costGuard.record(run.id(), stage, usage);
            if (runs.get(run.id()).getStatus() == RunStatus.CANCELLED) {
                // Stopped by the owner's cancel: the stage failing because of it must not turn the run into FAILED.
                Thread.interrupted();
                executions.finish(executionId, StageStatus.FAILED, usage, "Cancelled by owner");
                log.info("Run {} stage {} stopped by cancel", run.id(), stage);
                return false;
            }
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            executions.finish(executionId, StageStatus.FAILED, usage, reason);
            runs.fail(run.id(), stage, reason);
            notifications.send("StoryForge: run #" + run.id() + " failed at " + stage.folder() + ": " + reason
                    + "\n" + notifications.runLink(run.id()));
            log.error("Run {} stage {} failed", run.id(), stage, e);
        }
        return false;
    }
}
