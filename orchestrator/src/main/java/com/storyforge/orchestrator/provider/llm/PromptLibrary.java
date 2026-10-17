package com.storyforge.orchestrator.provider.llm;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PromptLibrary {

    public record Prompt(String name, String version, String tier, String system, String user) {
    }

    public record RenderedPrompt(String name, String version, String tier, String system, String user) {
    }

    private static final Pattern FRONT_MATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern SECTION = Pattern.compile("^##\\s*(system|user)\\s*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private final Path dir;

    public PromptLibrary(StoryForgeProperties properties) {
        this.dir = Path.of(properties.promptsDir());
    }

    public RenderedPrompt render(String name, Map<String, String> vars) {
        Prompt prompt = load(name);
        return new RenderedPrompt(name, prompt.version(), prompt.tier(), fill(prompt.system(), vars, name),
                fill(prompt.user(), vars, name));
    }

    public Prompt load(String name) {
        Path file = dir.resolve(name + ".md");
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt template missing: " + file.toAbsolutePath(), e);
        }
        Map<String, String> meta = new HashMap<>();
        Matcher front = FRONT_MATTER.matcher(text);
        if (front.find()) {
            for (String line : front.group(1).split("\\R")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    meta.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                            line.substring(colon + 1).strip());
                }
            }
            text = text.substring(front.end());
        }
        Map<String, String> sections = new HashMap<>();
        Matcher section = SECTION.matcher(text);
        String current = null;
        int start = 0;
        while (section.find()) {
            if (current != null) {
                sections.put(current, text.substring(start, section.start()).strip());
            }
            current = section.group(1).toLowerCase(Locale.ROOT);
            start = section.end();
        }
        if (current != null) {
            sections.put(current, text.substring(start).strip());
        }
        if (!sections.containsKey("user")) {
            throw new IllegalStateException("Prompt " + name + " has no '## user' section");
        }
        return new Prompt(name, meta.getOrDefault("version", "0"), meta.getOrDefault("tier", "fast"),
                sections.getOrDefault("system", ""), sections.get("user"));
    }

    private static String fill(String template, Map<String, String> vars, String name) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = vars.get(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException("Prompt " + name + " needs variable {{" + matcher.group(1) + "}}");
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
