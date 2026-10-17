package com.storyforge.orchestrator.provider.llm;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(prefix = "storyforge.llm.claude-code", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ClaudeCodeLlmProvider implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCodeLlmProvider.class);
    private static final int TAIL = 2000;

    private final StoryForgeProperties.Llm.ClaudeCode config;
    private final Path workDir;
    private final JsonMapper json;

    public ClaudeCodeLlmProvider(StoryForgeProperties properties, JsonMapper json) {
        this.config = properties.llm().claudeCode();
        this.workDir = Path.of(config.workDir()).toAbsolutePath();
        this.json = json;
    }

    @Override
    public String name() {
        return "claude-code";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String model = model(request.tier());
        String effort = effort(request.tier());
        String tools = config.stageTools() == null ? "" : config.stageTools().getOrDefault(request.stage().folder(), "");
        String id = UUID.randomUUID().toString();
        Path systemFile = workDir.resolve(id + ".system.md");
        Path stdout = workDir.resolve(id + ".out.json");
        Path stderr = workDir.resolve(id + ".err.log");
        long started = System.currentTimeMillis();
        try {
            Files.createDirectories(workDir);
            Files.writeString(systemFile, request.system(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(command(model, effort, tools, systemFile))
                    .directory(workDir.toFile())
                    .redirectOutput(stdout.toFile())
                    .redirectError(stderr.toFile())
                    .start();
            try (OutputStream in = process.getOutputStream()) {
                in.write(request.user().getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(config.timeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new LlmException("claude timed out after " + config.timeoutSeconds() + "s");
            }
            String output = Files.readString(stdout, StandardCharsets.UTF_8);
            if (process.exitValue() != 0 && output.isBlank()) {
                throw new LlmException("claude exited with " + process.exitValue() + ": " + tail(stderr));
            }
            LlmResponse response = parse(output, model, config.subscription(), System.currentTimeMillis() - started);
            log.info("claude-code {} {} in={} out={} {}ms", request.stage().folder(), model, response.inputTokens(),
                    response.outputTokens(), response.durationMs());
            return response;
        } catch (IOException e) {
            throw new LlmException("Cannot run '" + config.command() + "' (is Claude Code installed and logged in?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for claude", e);
        } finally {
            deleteQuietly(systemFile);
            deleteQuietly(stdout);
            deleteQuietly(stderr);
        }
    }

    List<String> command(String model, String effort, String tools, Path systemFile) {
        List<String> command = new ArrayList<>(List.of(config.command(), "-p",
                "--output-format", "json",
                "--model", model,
                "--system-prompt-file", systemFile.toString(),
                "--tools", tools,
                "--strict-mcp-config",
                "--disable-slash-commands",
                "--no-session-persistence",
                "--safe-mode",
                "--permission-mode", "dontAsk"));
        if (effort != null && !effort.isBlank()) {
            command.add("--effort");
            command.add(effort);
        }
        if (!tools.isBlank()) {
            command.add("--allowedTools");
            command.add(tools);
        }
        return command;
    }

    LlmResponse parse(String output, String model, boolean subscription, long durationMs) {
        JsonNode root;
        try {
            root = json.readTree(output);
        } catch (JacksonException e) {
            throw new LlmException("Unreadable claude output: " + output.substring(0, Math.min(output.length(), TAIL)));
        }
        if (root.path("is_error").asBoolean(false) || !"success".equals(root.path("subtype").asString(""))) {
            throw new LlmException("claude error (" + root.path("subtype").asString("?") + ", "
                    + root.path("api_error_status").asString("") + "): " + root.path("result").asString(""));
        }
        JsonNode usage = root.path("usage");
        long input = usage.path("input_tokens").asLong(0) + usage.path("cache_creation_input_tokens").asLong(0)
                + usage.path("cache_read_input_tokens").asLong(0);
        double notional = root.path("total_cost_usd").asDouble(0);
        BigDecimal cost = subscription ? BigDecimal.ZERO : BigDecimal.valueOf(notional);
        return new LlmResponse(root.path("result").asString(""), name(), model, input,
                usage.path("output_tokens").asLong(0), cost, durationMs);
    }

    private String model(String tier) {
        Map<String, String> models = config.models();
        return models.getOrDefault(tier == null ? "fast" : tier, models.get("fast"));
    }

    private String effort(String tier) {
        Map<String, String> efforts = config.efforts();
        return efforts == null ? null : efforts.get(tier == null ? "fast" : tier);
    }

    private static String tail(Path file) throws IOException {
        String text = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        return text.substring(Math.max(0, text.length() - TAIL));
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp file cleanup is best effort
        }
    }
}
