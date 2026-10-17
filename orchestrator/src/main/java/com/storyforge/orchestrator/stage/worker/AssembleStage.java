package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.edl.CaptionBuilder;
import com.storyforge.orchestrator.edl.EdlBuilder;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlAudio;
import com.storyforge.orchestrator.model.Edl.EdlCaption;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.provider.render.RenderProvider;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AssembleStage implements StageWorker {

    private static final int CAPTION_MAX_CHARS = 42;
    private static final double CAPTION_MAX_SECONDS = 3.5;

    private final RenderProvider renderer;
    private final StoryForgeProperties.Video video;

    public AssembleStage(RenderProvider renderer, StoryForgeProperties properties) {
        this.renderer = renderer;
        this.video = properties.video();
    }

    @Override
    public Stage stage() {
        return Stage.ASSEMBLE;
    }

    @Override
    public List<String> outputs() {
        return List.of("edl.json", "captions.srt", QaService.VIDEO);
    }

    @Override
    public void execute(StageContext context) {
        var files = context.files();
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        ShotList shots = files.read(Stage.VISUAL_PLAN, "shotlist.json", ShotList.class);
        AssetManifest assets = files.read(Stage.ASSETS, "assets.json", AssetManifest.class);
        Outline outline = files.read(Stage.OUTLINE, "outline.json", Outline.class);

        List<EdlCaption> captions = CaptionBuilder.build(alignment.words(), CAPTION_MAX_CHARS, CAPTION_MAX_SECONDS);
        List<EdlAudio> audio = List.of(new EdlAudio("mix",
                files.relative(files.path(Stage.AUDIO_MIX, AudioMixStage.MIX)), 0, 0, null));
        Edl edl = EdlBuilder.build(new EdlBuilder.Frame(video.width(), video.height(), video.fps()), scenes,
                alignment, shots, assets, outline, audio, captions);

        files.write(Stage.ASSEMBLE, "edl.json", edl);
        files.writeText(Stage.ASSEMBLE, "captions.srt", CaptionBuilder.toSrt(captions));
        renderer.render(edl, files.root(), files.path(Stage.ASSEMBLE, QaService.VIDEO));
        context.usage().provider(renderer.name());
    }
}
