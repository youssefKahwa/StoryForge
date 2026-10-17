package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.PackageDraft;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import com.storyforge.orchestrator.packaging.DescriptionBuilder;
import com.storyforge.orchestrator.packaging.TitleScorer;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.visual.CardRenderer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PackageStage implements StageWorker {

    private static final int TOP_TITLES = 3;
    private static final int MAX_THUMBNAIL_WORDS = 4;
    private static final int THUMB_WIDTH = 1280;
    private static final int THUMB_HEIGHT = 720;

    private final LlmRouter llm;
    private final JsonMapper json;

    public PackageStage(LlmRouter llm, JsonMapper json) {
        this.llm = llm;
        this.json = json;
    }

    @Override
    public Stage stage() {
        return Stage.PACKAGE;
    }

    @Override
    public List<String> outputs() {
        return List.of("package.json", "description.txt", "thumbnail.png");
    }

    @Override
    public void execute(StageContext context) {
        var files = context.files();
        TopicBrief brief = files.read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Edl edl = files.read(Stage.ASSEMBLE, "edl.json", Edl.class);
        Dossier dossier = files.read(Stage.RESEARCH, "dossier.json", Dossier.class);
        QaReport qa = files.read(Stage.QA, "qa_report.json", QaReport.class);
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });

        PackageDraft draft = llm.json(context, stage(), "package", Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "hook", scenes.get(0).narration(),
                "chapters", json.writeValueAsString(edl.chapters().stream().map(Edl.EdlChapter::title).toList())),
                PackageDraft.class);
        List<ScoredTitle> titles = TitleScorer.top(draft.titles(), brief.title(), TOP_TITLES);
        if (titles.isEmpty()) {
            throw new IllegalStateException("Packaging produced no usable titles");
        }
        String rawThumbnail = draft.thumbnailText() != null ? draft.thumbnailText() : brief.title();
        String thumbnailText = Arrays.stream(rawThumbnail.strip().split("\\s+"))
                .limit(MAX_THUMBNAIL_WORDS).collect(Collectors.joining(" "));
        CardRenderer.thumbnail(files.path(Stage.PACKAGE, "thumbnail.png"), THUMB_WIDTH, THUMB_HEIGHT,
                context.run().id(), thumbnailText);
        String description = DescriptionBuilder.build(draft.hookLine(), edl.chapters(), dossier.sources());
        files.writeText(Stage.PACKAGE, "description.txt", description);
        files.write(Stage.PACKAGE, "package.json", new VideoPackage(titles.get(0).title(), titles, description,
                draft.tags() == null ? List.of() : draft.tags(), draft.pinnedComment(),
                Stage.PACKAGE.folder() + "/thumbnail.png", thumbnailText, edl.chapters(), qa.disclosureRequired()));
    }
}
