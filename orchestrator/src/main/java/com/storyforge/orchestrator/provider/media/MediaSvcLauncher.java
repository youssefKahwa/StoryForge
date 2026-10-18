package com.storyforge.orchestrator.provider.media;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MediaSvcLauncher {

    private static final Logger log = LoggerFactory.getLogger(MediaSvcLauncher.class);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private final StoryForgeProperties.MediaSvc config;
    private final boolean needed;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Process process;

    public MediaSvcLauncher(StoryForgeProperties properties) {
        this.config = properties.mediaSvc();
        StoryForgeProperties.Providers providers = properties.providers();
        this.needed = "kokoro".equals(providers.tts()) || "whisper".equals(providers.alignment())
                || properties.sound().enabled();
    }

    public boolean needed() {
        return needed;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startIfNeeded() {
        if (needed && config.autostart() && !healthy()) {
            ensureRunning();
        }
    }

    public boolean healthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.url() + "/health"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public synchronized boolean ensureRunning() {
        if (healthy()) {
            return true;
        }
        if (!config.autostart()) {
            return false;
        }
        Path python = Path.of(config.python());
        Path dir = Path.of(config.dir()).toAbsolutePath();
        if (!Files.isRegularFile(python) || !Files.isRegularFile(dir.resolve("app.py"))) {
            log.warn("Cannot auto-start media-svc: missing {} or {}; run scripts/media-svc.ps1 -Install", python,
                    dir.resolve("app.py"));
            return false;
        }
        if (process == null || !process.isAlive()) {
            try {
                URI url = URI.create(config.url());
                ProcessBuilder builder = new ProcessBuilder(python.toString(), "-m", "uvicorn", "app:app", "--host",
                        url.getHost(), "--port", String.valueOf(url.getPort()))
                        .directory(dir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(dir.resolveSibling("media-svc.log").toFile());
                builder.environment().put("PYTHONIOENCODING", "utf-8");
                process = builder.start();
                log.info("Starting media-svc ({})", config.url());
            } catch (IOException e) {
                log.warn("Cannot start media-svc: {}", e.getMessage());
                return false;
            }
        }
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (healthy()) {
                log.info("media-svc is ready");
                return true;
            }
            if (!process.isAlive()) {
                log.warn("media-svc exited during startup; see media-svc.log next to the media-svc folder");
                return false;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return healthy();
    }

    @PreDestroy
    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }
}
