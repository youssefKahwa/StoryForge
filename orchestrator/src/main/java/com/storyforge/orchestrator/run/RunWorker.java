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
    private final boolean enabled;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "run-worker"));

    public RunWorker(RunService runs, PipelineEngine engine, SettingsService settings,
                     StoryForgeProperties properties) {
        this.runs = runs;
        this.engine = engine;
        this.settings = settings;
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
            executor.submit(() -> {
                try {
                    engine.execute(runId.get());
                } catch (Exception e) {
                    log.error("Run {} crashed", runId.get(), e);
                } finally {
                    busy.set(false);
                }
            });
        } catch (Exception e) {
            busy.set(false);
            log.error("Run worker poll failed", e);
        }
    }

    public boolean isBusy() {
        return busy.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
