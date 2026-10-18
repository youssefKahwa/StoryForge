package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.PreferencesDto;
import com.storyforge.orchestrator.settings.PreferencesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    private final PreferencesService preferences;

    public PreferencesController(PreferencesService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public PreferencesDto get() {
        return PreferencesDto.from(preferences.get());
    }

    @PutMapping
    public PreferencesDto save(@RequestBody PreferencesDto request) {
        return PreferencesDto.from(preferences.save(request.toPreferences()));
    }

    @PostMapping("/reset")
    public PreferencesDto reset() {
        return PreferencesDto.from(preferences.reset());
    }
}
