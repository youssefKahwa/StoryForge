package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.alignment.AlignmentAssembler;
import com.storyforge.orchestrator.provider.alignment.AlignmentProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import java.util.Map;
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
        var files = context.files();
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        NarrationManifest narration = files.read(Stage.NARRATE, "narration.json", NarrationManifest.class);
        Alignment alignment;
        if (files.exists(Stage.NARRATE, NarrateStage.CLIP_ALIGNMENTS)) {
            Map<String, ClipAlignment> clips = files.read(Stage.NARRATE, NarrateStage.CLIP_ALIGNMENTS,
                    new TypeReference<Map<String, ClipAlignment>>() { });
            alignment = AlignmentAssembler.assemble(aligner.name(), scenes, narration, clips);
        } else {
            alignment = aligner.align(scenes, narration, files.path(Stage.NARRATE, NarrateStage.NARRATION));
        }
        context.usage().provider(aligner.name());
        files.write(Stage.ALIGN, "alignment.json", alignment);
    }
}
