package com.storyforge.orchestrator.stage;

import java.util.List;

public interface StageWorker {

    Stage stage();

    List<String> outputs();

    void execute(StageContext context) throws Exception;
}
