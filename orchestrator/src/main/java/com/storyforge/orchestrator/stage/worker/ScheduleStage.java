package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.ScheduleSlot;
import com.storyforge.orchestrator.settings.Preferences;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduleStage implements StageWorker {

    private final PreferencesService preferences;
    private final StoryForgeProperties.Publish publish;
    private final Clock clock;

    public ScheduleStage(StoryForgeProperties properties, PreferencesService preferences) {
        this.publish = properties.publish();
        this.preferences = preferences;
        this.clock = Clock.systemUTC();
    }

    @Override
    public Stage stage() {
        return Stage.SCHEDULE;
    }

    @Override
    public List<String> outputs() {
        return List.of("schedule.json");
    }

    @Override
    public void execute(StageContext context) {
        context.usage().provider("slot-planner");
        context.files().write(Stage.SCHEDULE, "schedule.json", slot(Instant.now(clock)));
    }

    ScheduleSlot slot(Instant now) {
        Preferences prefs = preferences.get();
        ZoneId zone = ZoneId.of(prefs.publishZone());
        Instant vetoDeadline = now.plus(Duration.ofHours(publish.vetoHours()));
        ZonedDateTime candidate = vetoDeadline.atZone(zone)
                .with(TemporalAdjusters.nextOrSame(prefs.publishDay()))
                .with(LocalTime.parse(prefs.publishTime()));
        if (candidate.toInstant().isBefore(vetoDeadline)) {
            candidate = candidate.with(TemporalAdjusters.next(prefs.publishDay()));
        }
        return new ScheduleSlot(now, vetoDeadline, candidate.toInstant(), zone.getId());
    }
}
