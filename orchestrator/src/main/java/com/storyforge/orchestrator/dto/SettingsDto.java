package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SettingsDto(
        boolean paused,
        boolean workerBusy,
        String profile,
        BigDecimal budgetUsdPerVideo,
        int defaultTargetSeconds,
        StoryForgeProperties.Providers providers,
        List<String> llmDefaultRoute,
        Map<String, List<String>> llmRoutes,
        String llmOnly) {

    public static SettingsDto from(boolean paused, boolean workerBusy, String profile,
                                   StoryForgeProperties properties) {
        StoryForgeProperties.Llm llm = properties.llm();
        return new SettingsDto(paused, workerBusy, profile, properties.budget().maxUsdPerVideo(),
                properties.defaults().targetSeconds(), properties.providers(), llm.defaultRoute(),
                llm.routes() == null ? Map.of() : llm.routes(), llm.only());
    }
}
