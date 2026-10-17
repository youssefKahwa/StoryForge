package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.alignment.AlignmentProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AlignStage implements StageWorker {

    private final AlignmentProvider aligner;

    public AlignStage(AlignmentProvider aligner) {
        this.aligner = aligner;
    }

    @Override
    public Stage stage() {
        return Stage.ALIGN;
    }

    @Override
    public List<String> outputs() {
        return List.of("alignment.json");
    }

    @Override
    public void execute(StageContext context) {
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        NarrationManifest narration = context.files().read(Stage.NARRATE, "narration.json", NarrationManifest.class);
        Alignment alignment = aligner.align(scenes, narration,
                context.files().path(Stage.NARRATE, NarrateStage.NARRATION));
        context.usage().provider(aligner.name());
        context.files().write(Stage.ALIGN, "alignment.json", alignment);
    }
}
