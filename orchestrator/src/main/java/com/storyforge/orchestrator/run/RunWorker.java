package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.settings.SettingsService;
import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RunWorker {

    private static final Logger log = LoggerFactory.getLogger(RunWorker.class);

    private final RunService runs;
    private final PipelineEngine engine;
    private final SettingsService settings;
    private final KeepAwake keepAwake;
    private final boolean enabled;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "run-worker"));
    /** The run being worked on and its task, so a cancel can interrupt it (and the programs it waits for). */
    private volatile Long currentRun;
    private volatile java.util.concurrent.Future<?> current;

    public RunWorker(RunService runs, PipelineEngine engine, SettingsService settings, KeepAwake keepAwake,
                     StoryForgeProperties properties) {
        this.runs = runs;
        this.engine = engine;
        this.settings = settings;
        this.keepAwake = keepAwake;
        this.enabled = properties.worker().enabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        int recovered = runs.recoverInterrupted();
        if (recovered > 0) {
            log.info("Re-queued {} run(s) interrupted by a restart", recovered);
        }
    }

    @Scheduled(fixedDelayString = "${storyforge.worker.poll-ms}")
    public void poll() {
        if (!enabled || !busy.compareAndSet(false, true)) {
            return;
        }
        try {
            Optional<Long> runId = settings.isPaused() ? Optional.empty() : runs.claimNextQueued();
            if (runId.isEmpty()) {
                busy.set(false);
                return;
            }
            currentRun = runId.get();
            current = executor.submit(() -> {
                keepAwake.acquire();
                try {
                    engine.execute(runId.get());
                } catch (Exception e) {
                    log.error("Run {} crashed", runId.get(), e);
                } finally {
                    currentRun = null;
                    current = null;
                    keepAwake.release();
                    busy.set(false);
                }
            });
        } catch (Exception e) {
            busy.set(false);
            log.error("Run worker poll failed", e);
        }
    }

    /** Stops the work of a cancelled run now: its stage is interrupted and the programs it runs are killed. */
    public void interrupt(long runId) {
        java.util.concurrent.Future<?> task = current;
        if (task != null && Long.valueOf(runId).equals(currentRun)) {
            log.info("Run {} cancelled: stopping its current stage", runId);
            task.cancel(true);
        }
    }

    public boolean isBusy() {
        return busy.get();
    }

    @PreDestroy
    public void shutdown() {
        keepAwake.release();
        executor.shutdownNow();
    }
}
