package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.visual.ShotPlanner;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class VisualPlanStage implements StageWorker {

    private final FormatRegistry formats;
    private final ShotPlanner planner;

    public VisualPlanStage(FormatRegistry formats, StoryForgeProperties properties) {
        this.formats = formats;
        StoryForgeProperties.Shots shots = properties.shots();
        this.planner = new ShotPlanner(new ShotPlanner.Rules(shots.minSeconds(), shots.maxSeconds(),
                shots.maxSameType(), shots.interruptEverySeconds()));
    }

    @Override
    public Stage stage() {
        return Stage.VISUAL_PLAN;
    }

    @Override
    public List<String> outputs() {
        return List.of("shotlist.json");
    }

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = context.files().read(Stage.ALIGN, "alignment.json", Alignment.class);
        ShotList shots = planner.plan(scenes, alignment, formats.get(context.run().format()).style(),
                context.run().id() * 10_000L);
        context.usage().provider("shot-planner");
        context.files().write(Stage.VISUAL_PLAN, "shotlist.json", shots);
    }
}
