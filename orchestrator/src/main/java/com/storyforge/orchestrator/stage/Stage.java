package com.storyforge.orchestrator.stage;

import com.storyforge.orchestrator.run.RunState;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Stage {
    DISCOVER(RunState.CREATED, RunState.DISCOVERED),
    SELECT(RunState.DISCOVERED, RunState.SELECTED),
    RESEARCH(RunState.SELECTED, RunState.RESEARCHED),
    OUTLINE(RunState.RESEARCHED, RunState.OUTLINED),
    SCRIPT(RunState.OUTLINED, RunState.SCRIPTED),
    CRITIQUE(RunState.SCRIPTED, RunState.CRITIQUED),
    NARRATE(RunState.CRITIQUED, RunState.NARRATED),
    ALIGN(RunState.NARRATED, RunState.ALIGNED),
    VISUAL_PLAN(RunState.ALIGNED, RunState.VISUAL_PLANNED),
    ASSETS(RunState.VISUAL_PLANNED, RunState.ASSETS_READY),
    AUDIO_MIX(RunState.ASSETS_READY, RunState.AUDIO_MIXED),
    ASSEMBLE(RunState.AUDIO_MIXED, RunState.ASSEMBLED),
    QA(RunState.ASSEMBLED, RunState.QA_PASSED),
    PACKAGE(RunState.QA_PASSED, RunState.PACKAGED),
    SCHEDULE(RunState.PACKAGED, RunState.SCHEDULED),
    PUBLISH(RunState.SCHEDULED, RunState.PUBLISHED),
    LEARN(RunState.PUBLISHED, RunState.LEARNING);

    private final RunState from;
    private final RunState to;

    Stage(RunState from, RunState to) {
        this.from = from;
        this.to = to;
    }

    public RunState from() {
        return from;
    }

    public RunState to() {
        return to;
    }

    public String folder() {
        return name().toLowerCase(Locale.ROOT);
    }

    public List<Stage> andLater() {
        return Arrays.stream(values()).filter(stage -> stage.ordinal() >= ordinal()).toList();
    }

    public static Optional<Stage> after(RunState state) {
        return Arrays.stream(values()).filter(stage -> stage.from == state).findFirst();
    }

    public static Optional<Stage> fromFolder(String folder) {
        return Arrays.stream(values()).filter(stage -> stage.folder().equals(folder)).findFirst();
    }
}
