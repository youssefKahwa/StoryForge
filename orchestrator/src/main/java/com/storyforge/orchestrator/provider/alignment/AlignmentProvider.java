package com.storyforge.orchestrator.provider.alignment;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.Scene;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface AlignmentProvider {

    String name();

    Alignment align(List<Scene> scenes, NarrationManifest narration, Path narrationWav);

    default Optional<ClipAlignment> alignClip(Path wav, String text) {
        return Optional.empty();
    }
}
