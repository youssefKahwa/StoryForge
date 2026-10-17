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
        String topic = request.vars().getOrDefault("topic", "the story");
        String body = load("mock/llm/" + stage + ".json");
        if (body != null) {
            topic = topic.replace("\\", "\\\\").replace("\"", "\\\"");
        } else {
            body = load("mock/llm/" + stage + ".md");
        }
        if (body == null) {
            throw new LlmException("No canned mock response for stage " + stage);
        }
        String text = body.replace("{{topic}}", topic);
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
