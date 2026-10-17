package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.model.Edl;
import java.nio.file.Path;

public interface RenderProvider {

    String name();

    void render(Edl edl, Path runRoot, Path outputMp4);
}
