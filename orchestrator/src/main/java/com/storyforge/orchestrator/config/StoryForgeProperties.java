package com.storyforge.orchestrator.config;

import com.storyforge.orchestrator.format.VideoFormat;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("storyforge")
public record StoryForgeProperties(
        String dataDir,
        String promptsDir,
        String configDir,
        List<String> corsOrigins,
        Defaults defaults,
        Worker worker,
        Budget budget,
        Media media,
        Video video,
        Shots shots,
        Providers providers,
        Llm llm,
        Qa qa,
        Publish publish,
        MediaSvc mediaSvc,
        Narration narration,
        Cloudflare cloudflare,
        RenderSvc renderSvc) {

    public record RenderSvc(String dir, String source, String node, long timeoutSeconds, int concurrency,
                            boolean burnCaptions) {
    }

    public record Cloudflare(String accountId, String apiToken, String imageModel, int steps, long timeoutSeconds) {
    }

    public record MediaSvc(String url, long timeoutSeconds) {
    }

    public record Narration(String voice, double speed, String lang, double maxWer, double retrySpeedFactor) {
    }

    public record Defaults(int targetSeconds, VideoFormat format, String language) {
    }

    public record Worker(boolean enabled, long pollMs) {
    }

    public record Budget(BigDecimal maxUsdPerVideo) {
    }

    public record Media(String ffmpeg, String ffprobe, long timeoutSeconds) {
    }

    public record Video(int width, int height, int fps, int wordsPerMinute) {
    }

    public record Shots(double minSeconds, double maxSeconds, int maxSameType, double interruptEverySeconds) {
    }

    public record Providers(String tts, String image, String alignment, String render, String publish) {
    }

    public record Llm(boolean cache, String only, List<String> defaultRoute, Map<String, List<String>> routes,
                      int maxRepairs, int maxRewrites, ClaudeCode claudeCode) {

        public record ClaudeCode(boolean enabled, String command, String workDir, long timeoutSeconds,
                                 boolean subscription, Map<String, String> models, Map<String, String> efforts,
                                 Map<String, String> stageTools) {
        }

        public List<String> routeFor(String stage) {
            if (only != null && !only.isBlank()) {
                return List.of(only);
            }
            return routes != null && routes.containsKey(stage) ? routes.get(stage) : defaultRoute;
        }
    }

    public record Qa(double loudnessTarget, double loudnessTolerance, double truePeakMax,
                     double durationTolerance, int minSources, double criticAverage, double criticHook) {
    }

    public record Publish(DayOfWeek day, LocalTime time, String zone, int vetoHours) {
    }
}
