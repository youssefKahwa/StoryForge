package com.storyforge.orchestrator.settings;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PreferencesService {

    private static final String KEY = "preferences";
    public static final String DEFAULT_BRIEF = "English-language long-form storytelling channel: films, anime, true "
            + "stories and mysteries, told by a passionate, funny, opinionated storyteller who talks to the viewer "
            + "like a friend and makes them feel why the story matters. Facts right, opinions clearly opinions.";
    /** The brief used before v3: kept to recognise it and upgrade it to the new default. */
    static final String OLD_DEFAULT_BRIEF = "English-language long-form storytelling channel: true stories, "
            + "unsolved mysteries, historical disasters and incredible survivals, told like a documentary film for "
            + "curious adults. Calm, confident narrator; facts over speculation.";
    private static final List<String> VOLUMES = List.of("off", "low", "normal", "high");

    private final AppSettingRepository repository;
    private final StoryForgeProperties properties;
    private final JsonMapper json;

    public PreferencesService(AppSettingRepository repository, StoryForgeProperties properties, JsonMapper json) {
        this.repository = repository;
        this.properties = properties;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Preferences get() {
        return saved().orElseGet(this::defaults);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Preferences> saved() {
        return repository.findById(KEY).map(setting -> upgrade(json.readValue(setting.getValue(), Preferences.class)));
    }

    /** Saved before v3: the documentary brief made scripts sound robotic, so the untouched old default is replaced. */
    private static Preferences upgrade(Preferences p) {
        if (!OLD_DEFAULT_BRIEF.equals(p.channelBrief())) {
            return p;
        }
        return new Preferences(p.defaultVoice(), p.defaultMinutes(), p.defaultFormat(), p.shortsEnabled(),
                p.shortsCount(), p.burnCaptions(), p.upscaleImages(), p.musicVolume(), p.publishDay(),
                p.publishTime(), p.publishZone(), DEFAULT_BRIEF, p.hostMascot(), p.fairUseClips(),
                p.clipSharePercent());
    }

    @Transactional
    public Preferences save(Preferences update) {
        Preferences clean = validate(update);
        String value = json.writeValueAsString(clean);
        repository.findById(KEY).ifPresentOrElse(setting -> setting.setValue(value),
                () -> repository.save(new AppSetting(KEY, value)));
        return clean;
    }

    @Transactional
    public Preferences reset() {
        repository.deleteById(KEY);
        return defaults();
    }

    public Preferences defaults() {
        StoryForgeProperties.Publish publish = properties.publish();
        StoryForgeProperties.Shorts shorts = properties.shorts();
        return new Preferences(
                properties.narration().voice(),
                Math.round(properties.defaults().targetSeconds() / 6.0) / 10.0,
                properties.defaults().format(),
                shorts != null && shorts.enabled(),
                shorts == null ? 3 : shorts.count(),
                properties.renderSvc() != null && properties.renderSvc().burnCaptions(),
                properties.upscaler() != null && properties.upscaler().enabled(),
                "normal",
                publish.day(),
                publish.time().toString(),
                publish.zone(),
                DEFAULT_BRIEF,
                true,
                true,
                Preferences.DEFAULT_CLIP_SHARE);
    }

    private Preferences validate(Preferences p) {
        Preferences base = defaults();
        if (p.defaultMinutes() < Preferences.MIN_LONG_MINUTES || p.defaultMinutes() > 30) {
            throw new IllegalArgumentException("Default length must be between 8 and 30 minutes (8+ minutes allows "
                    + "mid-roll ads)");
        }
        if (p.clipSharePercent() != null
                && (p.clipSharePercent() < 0 || p.clipSharePercent() > Preferences.MAX_CLIP_SHARE)) {
            throw new IllegalArgumentException("Trailer clip share must be between 0 and "
                    + Preferences.MAX_CLIP_SHARE + "%");
        }
        if (p.shortsCount() < 0 || p.shortsCount() > 5) {
            throw new IllegalArgumentException("Shorts per video must be between 0 and 5");
        }
        if (p.channelBrief() != null && p.channelBrief().length() > 2000) {
            throw new IllegalArgumentException("Channel brief must be at most 2000 characters");
        }
        if (p.musicVolume() != null && !VOLUMES.contains(p.musicVolume())) {
            throw new IllegalArgumentException("Music volume must be one of " + VOLUMES);
        }
        try {
            LocalTime.parse(p.publishTime());
            ZoneId.of(p.publishZone());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Publish time must be HH:mm and the zone a valid time zone "
                    + "(for example America/New_York)");
        }
        return new Preferences(
                p.defaultVoice() == null || p.defaultVoice().isBlank() ? base.defaultVoice() : p.defaultVoice(),
                p.defaultMinutes(),
                p.defaultFormat() == null ? base.defaultFormat() : p.defaultFormat(),
                p.shortsEnabled(),
                p.shortsCount(),
                p.burnCaptions(),
                p.upscaleImages(),
                p.musicVolume() == null ? "normal" : p.musicVolume(),
                p.publishDay() == null ? base.publishDay() : p.publishDay(),
                p.publishTime(),
                p.publishZone(),
                p.channelBrief() == null || p.channelBrief().isBlank() ? base.channelBrief() : p.channelBrief().strip(),
                p.hostEnabled(),
                p.fairUseClipsEnabled(),
                (int) Math.round(p.clipShare() * 100));
    }
}
