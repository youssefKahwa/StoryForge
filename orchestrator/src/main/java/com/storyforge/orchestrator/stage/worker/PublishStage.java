package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.Publication;
import com.storyforge.orchestrator.model.ScheduleSlot;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.provider.publish.PublishProvider;
import com.storyforge.orchestrator.provider.publish.PublishRequest;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PublishStage implements StageWorker {

    private final PublishProvider publisher;

    public PublishStage(PublishProvider publisher) {
        this.publisher = publisher;
    }

    @Override
    public Stage stage() {
        return Stage.PUBLISH;
    }

    @Override
    public List<String> outputs() {
        return List.of("publication.json");
    }

    @Override
    public void execute(StageContext context) {
        var files = context.files();
        VideoPackage pkg = files.read(Stage.PACKAGE, "package.json", VideoPackage.class);
        ScheduleSlot slot = files.read(Stage.SCHEDULE, "schedule.json", ScheduleSlot.class);
        Publication publication = publisher.publish(new PublishRequest(context.run().id(), pkg.title(),
                pkg.description(), pkg.tags(), files.path(Stage.ASSEMBLE, QaService.VIDEO),
                files.path(Stage.PACKAGE, "thumbnail.png"), files.path(Stage.ASSEMBLE, "captions.srt"),
                slot.publishAt(), pkg.disclosureRequired()));
        context.usage().provider(publisher.name());
        files.write(Stage.PUBLISH, "publication.json", publication);
    }
}
