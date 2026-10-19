package com.storyforge.orchestrator.provider.llm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MockLlmProvider implements LlmProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        String stage = request.stage().folder();
        String prompt = request.prompt();
        boolean jsonBody = true;
        String body = prompt == null ? null : load("mock/llm/prompt-" + prompt + ".json");
        if (body == null && prompt != null) {
            body = load("mock/llm/prompt-" + prompt + ".md");
            jsonBody = body == null;
        }
        if (body == null) {
            body = load("mock/llm/" + stage + ".json");
            jsonBody = true;
        }
        if (body == null) {
            body = load("mock/llm/" + stage + ".md");
            jsonBody = false;
        }
        if (body == null) {
            throw new LlmException("No canned mock response for stage " + stage);
        }
        java.util.Map<String, String> vars = new java.util.HashMap<>(request.vars());
        vars.putIfAbsent("topic", "the story");
        String text = body;
        for (var entry : vars.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            text = text.replace("{{" + entry.getKey() + "}}",
                    jsonBody ? value.replace("\\", "\\\\").replace("\"", "\\\"") : value);
        }
        long input = (request.system().length() + request.user().length()) / 4;
        return new LlmResponse(text, name(), "canned", input, text.length() / 4, BigDecimal.ZERO, 0);
    }

    private static String load(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
