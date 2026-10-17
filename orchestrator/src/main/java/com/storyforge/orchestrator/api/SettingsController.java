package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.dto.KillSwitchRequest;
import com.storyforge.orchestrator.dto.SettingsDto;
import com.storyforge.orchestrator.run.RunWorker;
import com.storyforge.orchestrator.settings.SettingsService;
import jakarta.validation.Valid;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final RunWorker worker;
    private final StoryForgeProperties properties;
    private final String profile;

    public SettingsController(SettingsService settings, RunWorker worker, StoryForgeProperties properties,
                              Environment environment) {
        this.settings = settings;
        this.worker = worker;
        this.properties = properties;
        this.profile = String.join(",", environment.getActiveProfiles());
    }

    @GetMapping
    public SettingsDto get() {
        return SettingsDto.from(settings.isPaused(), worker.isBusy(), profile, properties);
    }

    @PutMapping("/kill-switch")
    public SettingsDto killSwitch(@Valid @RequestBody KillSwitchRequest request) {
        settings.setPaused(request.paused());
        return get();
    }
}
