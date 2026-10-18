package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.config.StoryForgeProperties;
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

    private static final Logger log = LoggerFactory.getLogger(RemotionRenderer.class);
    private static final List<String> SYNCED = List.of("package.json", "render.mjs", "still.mjs", "src");
    private static final int TAIL = 3000;
    private static final long STILL_TIMEOUT_SECONDS = 300;

    private final StoryForgeProperties.RenderSvc config;
    private final Path installDir;
    private final Path sourceDir;
    private final JsonMapper json;

    public RemotionRenderer(StoryForgeProperties properties, JsonMapper json) {
        this.json = json;
        this.config = properties.renderSvc();
        this.installDir = Path.of(config.dir()).toAbsolutePath();
        this.sourceDir = Path.of(config.source()).toAbsolutePath();
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
        Path edlFile = outputMp4.resolveSibling("edl.json");
        Path logFile = outputMp4.resolveSibling("render.log");
        List<String> command = new ArrayList<>(List.of(config.node(), installDir.resolve("render.mjs").toString(),
                edlFile.toString(), runRoot.toAbsolutePath().toString(), outputMp4.toAbsolutePath().toString()));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(installDir.toFile())
                    .redirectErrorStream(true).redirectOutput(logFile.toFile());
            if (config.concurrency() > 0) {
                builder.environment().put("RENDER_CONCURRENCY", String.valueOf(config.concurrency()));
            }
            log.info("Remotion render started: {} frames", Math.round(edl.durationSec() * edl.fps()));
            Process process = builder.start();
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MediaException("Remotion render timed out after " + config.timeoutSeconds() + "s");
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
            if (config.concurrency() > 0) {
                builder.environment().put("RENDER_CONCURRENCY", String.valueOf(config.concurrency()));
            }
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
