package com.storyforge.orchestrator.provider.llm;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.provider.llm.PromptLibrary.RenderedPrompt;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);
    private static final String REPAIR_SYSTEM = "You repair malformed JSON. Reply with the corrected JSON only.";

    private final Map<String, LlmProvider> providers;
    private final StoryForgeProperties.Llm config;
    private final LlmCache cache;
    private final PromptLibrary prompts;
    private final JsonMapper json;

    public LlmRouter(List<LlmProvider> providers, StoryForgeProperties properties, LlmCache cache,
                     PromptLibrary prompts, JsonMapper json) {
        this.providers = providers.stream().collect(Collectors.toMap(LlmProvider::name, Function.identity()));
        this.config = properties.llm();
        this.cache = cache;
        this.prompts = prompts;
        this.json = json;
    }

    public String text(StageContext context, Stage stage, String promptName, Map<String, String> vars) {
        RenderedPrompt prompt = prompts.render(promptName, vars);
        return complete(context, request(stage, prompt, vars)).text();
    }

    public <T> T json(StageContext context, Stage stage, String promptName, Map<String, String> vars,
                      Class<T> type) {
        RenderedPrompt prompt = prompts.render(promptName, vars);
        String text = complete(context, request(stage, prompt, vars)).text();
        for (int attempt = 0; ; attempt++) {
            try {
                return json.readValue(extractJson(text), type);
            } catch (JacksonException e) {
                if (attempt >= config.maxRepairs()) {
                    throw new LlmException("Invalid JSON for " + promptName + " after " + attempt + " repair(s): "
                            + e.getOriginalMessage());
                }
                log.warn("Repairing JSON for {} (attempt {}): {}", promptName, attempt + 1, e.getOriginalMessage());
                LlmRequest repair = new LlmRequest(stage, prompt.version() + "+repair", REPAIR_SYSTEM,
                        "Parser error: " + e.getOriginalMessage() + "\n\nJSON to fix:\n" + text, prompt.tier(), vars);
                text = complete(context, repair).text();
            }
        }
    }

    private LlmRequest request(Stage stage, RenderedPrompt prompt, Map<String, String> vars) {
        return new LlmRequest(stage, prompt.version(), prompt.system(), prompt.user(), prompt.tier(), vars);
    }

    private LlmResponse complete(StageContext context, LlmRequest request) {
        List<String> route = config.routeFor(request.stage().folder());
        String key = cache.key(request, context.run().cacheSalt(), route);
        context.usage().promptVersion(request.promptVersion());
        Optional<LlmResponse> cached = cache.get(key);
        if (cached.isPresent()) {
            context.usage().provider(cached.get().provider() + ":cache");
            return cached.get();
        }
        List<String> errors = new ArrayList<>();
        for (String name : route) {
            LlmProvider provider = providers.get(name);
            if (provider == null) {
                errors.add(name + ": not configured");
                continue;
            }
            try {
                LlmResponse response = provider.complete(request);
                context.usage().call(response.provider(), response.inputTokens(), response.outputTokens(),
                        response.costUsd());
                cache.put(key, response);
                return response;
            } catch (LlmException e) {
                log.warn("LLM provider {} failed for {}: {}", name, request.stage(), e.getMessage());
                errors.add(name + ": " + e.getMessage());
            }
        }
        throw new LlmException("All LLM providers failed for " + request.stage().folder() + " " + errors);
    }

    static String extractJson(String text) {
        String trimmed = text.strip();
        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int bodyStart = trimmed.indexOf('\n', fence);
            int fenceEnd = trimmed.indexOf("```", bodyStart + 1);
            if (bodyStart > 0 && fenceEnd > bodyStart) {
                trimmed = trimmed.substring(bodyStart + 1, fenceEnd).strip();
            }
        }
        int object = trimmed.indexOf('{');
        int array = trimmed.indexOf('[');
        int start = object < 0 ? array : array < 0 ? object : Math.min(object, array);
        if (start < 0) {
            return trimmed;
        }
        int end = Math.max(trimmed.lastIndexOf('}'), trimmed.lastIndexOf(']'));
        return end > start ? trimmed.substring(start, end + 1) : trimmed.substring(start);
    }
}
