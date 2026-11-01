package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.run.PowerStatus;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.Edl;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "render", havingValue = "remotion")
public class RemotionRenderer implements RenderProvider {

    /** A render whose log shows no progress this long is stuck (a crashed browser that never came back). */
    private static final long STALL_MS = 10 * 60 * 1000L;
    private static final Logger log = LoggerFactory.getLogger(RemotionRenderer.class);
    private static final List<String> SYNCED = List.of("package.json", "render.mjs", "still.mjs", "src");
    private static final int TAIL = 3000;
    private static final long STILL_TIMEOUT_SECONDS = 300;

    private final StoryForgeProperties.RenderSvc config;
    private final Path installDir;
    private final Path sourceDir;
    private final JsonMapper json;
    private final PowerStatus power;

    public RemotionRenderer(StoryForgeProperties properties, JsonMapper json, PowerStatus power) {
        this.json = json;
        this.power = power;
        this.config = properties.renderSvc();
        this.installDir = Path.of(config.dir()).toAbsolutePath();
        this.sourceDir = Path.of(config.source()).toAbsolutePath();
    }

    private static final double GB_PER_TAB = 0.9;
    private static final int SAFE_TABS = 2;

    /**
     * Parallel browser tabs for this render: the configured number, but no more than free memory allows (~0.9 GB a
     * tab; VS Code and Chrome often leave only 2-4 GB) and only 2 on battery, where each frame is much slower.
     */
    private int concurrency() {
        int configured = config.concurrency() > 0 ? config.concurrency() : 4;
        double freeGb = freeMemoryGb();
        int byMemory = Math.max(1, (int) Math.floor(freeGb / GB_PER_TAB));
        int tabs = Math.min(configured, byMemory);
        if (power.onBattery()) {
            tabs = Math.min(SAFE_TABS, tabs);
        }
        log.info("Rendering with {} tab(s) ({} GB free{})", tabs, String.format(java.util.Locale.ROOT, "%.1f", freeGb),
                power.onBattery() ? ", on battery: plug in for full speed" : "");
        return tabs;
    }

    private static double freeMemoryGb() {
        if (java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean os) {
            return os.getFreeMemorySize() / (1024.0 * 1024 * 1024);
        }
        return 4;
    }

    @Override
    public String name() {
        return "remotion";
    }

    @Override
    public void render(Edl edl, Path runRoot, Path outputMp4) {
        syncSources();
        if (!Files.isDirectory(installDir.resolve("node_modules").resolve("@remotion"))) {
            throw new MediaException("Remotion is not installed in " + installDir
                    + ". Run scripts/render-svc.ps1 -Install once.");
        }
        int tabs = concurrency();
        try {
            // The Intel GPU (ANGLE) renders about 12 frames/s here, 3x Chrome's default.
            renderOnce(edl, runRoot, outputMp4, tabs, "angle");
        } catch (MediaException e) {
            // A crash, out of memory or a stalled render: once more on Chrome's default GL with fewer tabs.
            log.warn("Render failed with the GPU and {} tabs ({}); rendering again with Chrome's default GL and {}",
                    tabs, e.getMessage() == null ? "?" : e.getMessage().lines().findFirst().orElse("?"),
                    Math.min(tabs, SAFE_TABS));
            renderOnce(edl, runRoot, outputMp4, Math.min(tabs, SAFE_TABS), "default");
        }
    }

    private void renderOnce(Edl edl, Path runRoot, Path outputMp4, int tabs, String gl) {
        Path edlFile = outputMp4.resolveSibling("edl.json");
        Path logFile = outputMp4.resolveSibling("render.log");
        List<String> command = new ArrayList<>(List.of(config.node(), installDir.resolve("render.mjs").toString(),
                edlFile.toString(), runRoot.toAbsolutePath().toString(), outputMp4.toAbsolutePath().toString()));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(installDir.toFile())
                    .redirectErrorStream(true).redirectOutput(logFile.toFile());
            builder.environment().put("RENDER_CONCURRENCY", String.valueOf(tabs));
            builder.environment().put("RENDER_GL", gl);
            log.info("Remotion render started: {} frames ({} GL)", Math.round(edl.durationSec() * edl.fps()), gl);
            Process process = builder.start();
            long deadline = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;
            while (!process.waitFor(15, TimeUnit.SECONDS)) {
                long quiet = System.currentTimeMillis() - Files.getLastModifiedTime(logFile).toMillis();
                if (quiet > STALL_MS || System.currentTimeMillis() > deadline) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    throw new MediaException(quiet > STALL_MS
                            ? "Remotion render stalled (no progress for " + STALL_MS / 60000 + " min)"
                            : "Remotion render timed out after " + config.timeoutSeconds() + "s");
                }
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputMp4)) {
                String output = Files.readString(logFile, StandardCharsets.UTF_8);
                throw new MediaException("Remotion render failed (exit " + process.exitValue() + "): "
                        + output.substring(Math.max(0, output.length() - TAIL)));
            }
        } catch (IOException e) {
            throw new MediaException("Cannot start node for Remotion (is Node.js on PATH?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during Remotion render", e);
        }
    }

    @Override
    public boolean supportsShorts() {
        return true;
    }

    @Override
    public void renderShort(Map<String, Object> spec, Path runRoot, Path outputMp4) {
        syncSources();
        Path specFile = outputMp4.resolveSibling(outputMp4.getFileName() + ".json");
        Path logFile = outputMp4.resolveSibling(outputMp4.getFileName() + ".log");
        try {
            Files.writeString(specFile, json.writeValueAsString(spec), StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(config.node(), installDir.resolve("render.mjs").toString(),
                    specFile.toString(), runRoot.toAbsolutePath().toString(), outputMp4.toAbsolutePath().toString(),
                    "Short").directory(installDir.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile());
            builder.environment().put("RENDER_CONCURRENCY", String.valueOf(concurrency()));
            Process process = builder.start();
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Short render timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputMp4)) {
                String output = Files.readString(logFile, StandardCharsets.UTF_8);
                throw new MediaException("Short render failed: " + output.substring(Math.max(0, output.length() - TAIL)));
            }
        } catch (IOException e) {
            throw new MediaException("Cannot render Short with Remotion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during Short render", e);
        }
    }

    @Override
    public void renderThumbnail(Path background, String text, List<String> palette, Path outputPng) {
        syncSources();
        Path props = outputPng.resolveSibling(outputPng.getFileName() + ".props.json");
        Path logFile = outputPng.resolveSibling(outputPng.getFileName() + ".log");
        try {
            Files.writeString(props, json.writeValueAsString(Map.of(
                    "background", background == null ? "" : background.toAbsolutePath().toString(),
                    "text", text, "palette", palette == null ? List.of() : palette)), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(config.node(), installDir.resolve("still.mjs").toString(),
                    props.toString(), outputPng.toAbsolutePath().toString())
                    .directory(installDir.toFile()).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            if (!process.waitFor(STILL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Thumbnail render timed out");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(outputPng)) {
                throw new MediaException("Thumbnail render failed: " + Files.readString(logFile, StandardCharsets.UTF_8));
            }
            Files.deleteIfExists(props);
            Files.deleteIfExists(logFile);
        } catch (IOException e) {
            throw new MediaException("Cannot render thumbnail with Remotion", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaException("Interrupted during thumbnail render", e);
        }
    }

    private void syncSources() {
        if (!Files.isDirectory(sourceDir)) {
            return;
        }
        for (String entry : SYNCED) {
            Path from = sourceDir.resolve(entry);
            if (!Files.exists(from)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(from)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    Path target = installDir.resolve(sourceDir.relativize(file).toString());
                    if (!Files.exists(target) || Files.getLastModifiedTime(target)
                            .compareTo(Files.getLastModifiedTime(file)) < 0) {
                        Files.createDirectories(target.getParent());
                        Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
