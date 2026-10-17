package com.storyforge.orchestrator.settings;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private static final String PAUSED = "pipeline.paused";

    private final AppSettingRepository repository;

    public SettingsService(AppSettingRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public boolean isPaused() {
        return repository.findById(PAUSED).map(setting -> Boolean.parseBoolean(setting.getValue())).orElse(false);
    }

    @Transactional
    public void setPaused(boolean paused) {
        repository.findById(PAUSED)
                .ifPresentOrElse(setting -> setting.setValue(String.valueOf(paused)),
                        () -> repository.save(new AppSetting(PAUSED, String.valueOf(paused))));
    }
}
