package com.storyforge.orchestrator.revision;

import com.storyforge.orchestrator.stage.Stage;

/** What a revision changes, and the earliest stage that must run again for it. */
public enum RevisionKind {
    NARRATION(Stage.NARRATE),
    IMAGE(Stage.VISUAL_PLAN),
    MUSIC(Stage.AUDIO_MIX),
    PACKAGE(Stage.PACKAGE);

    private final Stage rerunFrom;

    RevisionKind(Stage rerunFrom) {
        this.rerunFrom = rerunFrom;
    }

    public Stage rerunFrom() {
        return rerunFrom;
    }
}
