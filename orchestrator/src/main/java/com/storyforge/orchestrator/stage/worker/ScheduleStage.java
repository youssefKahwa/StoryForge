package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.ScheduleSlot;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScheduleStage implements StageWorker {

    private final StoryForgeProperties.Publish publish;
    private final Clock clock;

    public ScheduleStage(StoryForgeProperties properties) {
        this.publish = properties.publish();
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
        ZoneId zone = ZoneId.of(publish.zone());
        Instant vetoDeadline = now.plus(Duration.ofHours(publish.vetoHours()));
        ZonedDateTime candidate = vetoDeadline.atZone(zone)
                .with(TemporalAdjusters.nextOrSame(publish.day()))
                .with(publish.time());
        if (candidate.toInstant().isBefore(vetoDeadline)) {
            candidate = candidate.with(TemporalAdjusters.next(publish.day()));
        }
        return new ScheduleSlot(now, vetoDeadline, candidate.toInstant(), zone.getId());
    }
}
