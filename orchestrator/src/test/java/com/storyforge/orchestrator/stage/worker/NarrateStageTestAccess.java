package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.ClipAlignment;

public final class NarrateStageTestAccess {

    private NarrateStageTestAccess() {
    }

    public static ClipAlignment scale(ClipAlignment alignment, double tempo) {
        return NarrateStage.scale(alignment, tempo);
    }
}
